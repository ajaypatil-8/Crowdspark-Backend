// src/main/java/Crowdspark/Crowdspark/service/impl/PushNotificationServiceImpl.java

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.FcmToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.FcmTokenRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.PushNotificationService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationServiceImpl implements PushNotificationService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository     userRepository;

    // ── Subscribe ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void subscribe(Long userId, String token, String deviceHint) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        fcmTokenRepository.findByUser_IdAndToken(userId, token)
                .ifPresentOrElse(
                        existing -> {
                            // Refresh lastUsedAt and deviceHint
                            existing.setLastUsedAt(LocalDateTime.now());
                            if (deviceHint != null) existing.setDeviceHint(deviceHint);
                            fcmTokenRepository.save(existing);
                            log.debug("FCM token refreshed for user={}", userId);
                        },
                        () -> {
                            FcmToken fcm = new FcmToken();
                            fcm.setUser(user);
                            fcm.setToken(token);
                            fcm.setDeviceHint(deviceHint);
                            fcm.setLastUsedAt(LocalDateTime.now());
                            fcmTokenRepository.save(fcm);
                            log.info("FCM token registered for user={} device='{}'",
                                    userId, deviceHint);
                        }
                );
    }

    // ── Unsubscribe ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void unsubscribe(Long userId, String token) {
        fcmTokenRepository.findByUser_IdAndToken(userId, token)
                .ifPresent(fcmTokenRepository::delete);
        log.info("FCM token unsubscribed for user={}", userId);
    }

    // ── Unsubscribe all ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void unsubscribeAll(Long userId) {
        fcmTokenRepository.deleteAllByUserId(userId);
        log.info("All FCM tokens removed for user={}", userId);
    }

    // ── Send push ─────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendToUser(User user, String title, String body, String link, String iconUrl) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialised — skipping push for user={}", user.getId());
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findByUser_Id(user.getId());
        if (tokens.isEmpty()) return;

        FirebaseMessaging fm = FirebaseMessaging.getInstance();

        for (FcmToken fcmToken : tokens) {
            try {
                Message message = buildMessage(fcmToken.getToken(), title, body, link, iconUrl);
                String response = fm.send(message);

                // Update lastUsedAt on success
                fcmToken.setLastUsedAt(LocalDateTime.now());
                fcmTokenRepository.save(fcmToken);

                log.debug("FCM push sent to user={} token={}... msgId={}",
                        user.getId(),
                        fcmToken.getToken().substring(0, Math.min(20, fcmToken.getToken().length())),
                        response);

            } catch (FirebaseMessagingException e) {
                handleFcmError(e, fcmToken);
            } catch (Exception e) {
                log.error("Unexpected error sending FCM to user={}: {}",
                        user.getId(), e.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Message buildMessage(String token, String title, String body,
                                 String link, String iconUrl) {
        // BUG FIX (Feature #22): was `.setImage(iconUrl != null ? iconUrl : "")`.
        // An explicit empty string is a real (non-null) value sent to FCM's API,
        // not the same as omitting the field — FCM's image field is validated as
        // a URL when present, so "" risked the whole send failing, whereas simply
        // passing iconUrl through (null when not set) omits the field entirely,
        // which is what "no image" should actually look like. The WebpushNotification
        // below already got this right by falling back to a real icon path instead
        // of an empty string; this just makes the two consistent.
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .setImage(iconUrl)
                .build();

        // WebpushConfig carries the click_action (link) and icon for web browsers
        WebpushConfig webpushConfig = WebpushConfig.builder()
                .setNotification(
                        WebpushNotification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .setIcon(iconUrl != null ? iconUrl : "/icon-192.png")
                                .setBadge("/badge-72.png")
                                .build()
                )
                .setFcmOptions(
                        WebpushFcmOptions.builder()
                                .setLink(link)
                                .build()
                )
                .build();

        return Message.builder()
                .setToken(token)
                .setNotification(notification)
                .setWebpushConfig(webpushConfig)
                .build();
    }


    private void handleFcmError(FirebaseMessagingException e, FcmToken fcmToken) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT) {
            log.warn("Stale FCM token detected — deleting token for user={}. Code={}",
                    fcmToken.getUser().getId(), code);
            fcmTokenRepository.delete(fcmToken);
        } else {
            log.error("FCM delivery failed for user={}: code={} msg={}",
                    fcmToken.getUser().getId(), code, e.getMessage());
        }
    }
}