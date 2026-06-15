// src/main/java/Crowdspark/Crowdspark/service/PushNotificationService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.User;

public interface PushNotificationService {

    /**
     * Register (or refresh) an FCM token for a user.
     * Idempotent — calling with the same token twice is safe.
     */
    void subscribe(Long userId, String token, String deviceHint);

    /**
     * Remove a specific FCM token (user-initiated unsubscribe or browser revocation).
     */
    void unsubscribe(Long userId, String token);

    /**
     * Remove ALL FCM tokens for a user — called on account deletion.
     */
    void unsubscribeAll(Long userId);

    /**
     * Send a push notification to every registered device of a user.
     * Stale / invalid tokens are cleaned up automatically on delivery failure.
     *
     * @param user    Recipient
     * @param title   Notification title (shown in OS tray)
     * @param body    Notification body text
     * @param link    Deep-link URL opened when user taps the notification
     * @param iconUrl Optional icon URL (falls back to site icon if null)
     */
    void sendToUser(User user, String title, String body, String link, String iconUrl);
}
