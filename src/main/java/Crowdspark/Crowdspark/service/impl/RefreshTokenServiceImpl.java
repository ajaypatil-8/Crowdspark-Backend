// src/main/java/Crowdspark/Crowdspark/service/impl/RefreshTokenServiceImpl.java
// Feature #28 — Refresh Token Rotation Security
//
// KEY CHANGES vs the previous implementation:
//
// 1. FAMILY TRACKING
//    Every token now carries a familyId UUID shared by all tokens in the same
//    login session. create(userId) generates a new family; create(userId, familyId,
//    parentHash) continues it for rotation.
//
// 2. THEFT DETECTION
//    validate() now checks: if the token is REVOKED and has a familyId, this is
//    a replay of a previously-rotated token (theft signal). It immediately revokes
//    every token in the family, sends an async email security alert, and throws
//    a descriptive AuthException.
//
// 3. DOUBLE-HASH BUG FIX
//    The old AuthController called revoke(entity.getToken()) where entity.getToken()
//    was the SHA-256 hash returned from the DB. revoke() then hashed it again
//    (sha256(sha256(raw))). This meant revocation silently failed every time.
//    Fix: AuthController now calls revoke(rawToken) with the original raw string,
//    and we added revokeByHash(hash) for internal use where we already have the hash.
//
// 4. SCHEDULED CLEANUP
//    deleteExpiredTokens() runs nightly to hard-delete old rows and keep the table
//    from growing unboundedly.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.RefreshTokenRepository;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final EmailService            emailService;
    private final UserService             userService;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpirationMs;

    // ── create (new family — called at login) ─────────────────────────────────

    @Override
    public RefreshToken create(Long userId) {
        return create(userId, UUID.randomUUID().toString(), null);
    }

    // ── create (continue existing family — called during rotation) ───────────

    @Override
    public RefreshToken create(Long userId, String familyId, String parentTokenHash) {
        String rawToken    = UUID.randomUUID().toString();
        String hashedToken = DigestUtils.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setToken(hashedToken);           // store HASH in DB
        token.setUserId(userId);
        token.setRevoked(false);
        token.setFamilyId(familyId);
        token.setParentTokenHash(parentTokenHash);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiryDate(
                LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs))
        );

        RefreshToken saved = repository.save(token);

        // Return entity with RAW token so the controller can send it to the client.
        // We create a copy to avoid mutating the persisted entity.
        RefreshToken response = new RefreshToken();
        response.setId(saved.getId());
        response.setToken(rawToken);           // RAW — client receives this
        response.setUserId(saved.getUserId());
        response.setRevoked(saved.isRevoked());
        response.setFamilyId(saved.getFamilyId());
        response.setParentTokenHash(saved.getParentTokenHash());
        response.setCreatedAt(saved.getCreatedAt());
        response.setExpiryDate(saved.getExpiryDate());
        return response;
    }

    // ── validate — with theft detection ──────────────────────────────────────

    @Override
    public RefreshToken validate(String rawToken) {
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshToken token = repository.findByToken(hash)
                .orElseThrow(() -> new AuthException("Invalid refresh token."));

        // ── THEFT DETECTION ───────────────────────────────────────────────────
        // A revoked token being presented again means either:
        //   (a) a client is naively retrying a request with the old token
        //   (b) the original token was stolen and someone else is trying to use it
        //
        // We cannot distinguish (a) from (b) safely, so we treat ALL revoked-token
        // replays within a known family as potential theft and terminate the family.
        if (token.isRevoked()) {
            if (token.getFamilyId() != null) {
                log.warn("SECURITY: Refresh token reuse detected for userId={} familyId={}. " +
                         "Revoking entire family.", token.getUserId(), token.getFamilyId());

                // Revoke every token in this login session chain immediately
                repository.revokeAllByFamilyId(token.getFamilyId());

                // Send async email alert so the user knows their account may be compromised
                sendTheftAlertAsync(token.getUserId());

                throw new AuthException(
                    "SECURITY_ALERT: Your session was terminated because an expired " +
                    "token was reused — a possible sign of account compromise. " +
                    "All active sessions have been signed out. " +
                    "If this wasn't you, please reset your password immediately."
                );
            }
            // No family (legacy token from before Feature #28) — plain revoked error
            throw new AuthException("Refresh token has already been used. Please log in again.");
        }

        // ── Standard expiry check ─────────────────────────────────────────────
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            // Revoke it so it's cleaned up properly
            token.setRevoked(true);
            repository.save(token);
            throw new AuthException("Refresh token has expired. Please log in again.");
        }

        return token;
        // NOTE: token.getToken() on the returned entity contains the HASH (from DB).
        //       Controllers must use the original rawToken for revoke(); not entity.getToken().
    }

    // ── revoke (by raw token — use in controllers) ────────────────────────────

    @Override
    public void revoke(String rawToken) {
        String hash  = DigestUtils.sha256Hex(rawToken);
        revokeByHash(hash);
    }

    // ── revokeByHash (by stored hash — avoids double-hash if you already have hash) ─

    @Override
    public void revokeByHash(String tokenHash) {
        repository.findByToken(tokenHash).ifPresent(t -> {
            t.setRevoked(true);
            repository.save(t);
        });
        // Silent no-op if not found — idempotent for safety
    }

    // ── revokeFamily — terminate entire login session ─────────────────────────

    @Override
    public void revokeFamily(String familyId) {
        log.info("Revoking all tokens in family {}", familyId);
        repository.revokeAllByFamilyId(familyId);
    }

    // ── revokeAll — logout-all / account deletion ─────────────────────────────

    @Override
    public void revokeAll(Long userId) {
        repository.revokeAllByUserId(userId);
    }

    // ── Async theft-alert email ───────────────────────────────────────────────

    @Async
    public void sendTheftAlertAsync(Long userId) {
        try {
            // getById throws if not found — wrap in try-catch so email failure
            // never interrupts the theft-response flow (revocation already done)
            var user = userService.getById(userId);
            String subject = "⚠️ CrowdSpark Security Alert: Suspicious Login Detected";
            String body = String.format(
                "Hi %s,%n%n" +
                "We detected an attempt to reuse an expired session token on your%n" +
                "CrowdSpark account (%s).%n%n" +
                "As a precaution, ALL active sessions for your account have been%n" +
                "immediately signed out.%n%n" +
                "✅ What you should do now:%n" +
                "1. Log in again at crowdspark.in%n" +
                "2. If you did not expect this, go to Settings → Change Password%n" +
                "3. If you need help, contact us at security@crowdspark.in%n%n" +
                "This alert was sent because someone (possibly not you) tried to%n" +
                "use an old session token that had already been rotated.%n%n" +
                "– The CrowdSpark Security Team",
                user.getName(),
                user.getEmail()
            );
            emailService.sendSimpleEmail(user.getEmail(), subject, body);
            log.info("Security alert email sent to userId={}", userId);
        } catch (Exception e) {
            // Never let email failure bubble up — the revocation already happened
            log.error("Failed to send theft alert email for userId={}: {}", userId, e.getMessage());
        }
    }

    // ── Nightly cleanup — hard-delete expired tokens ──────────────────────────

    /**
     * Runs at 03:00 every night. Hard-deletes tokens that expired more than
     * 7 days ago to keep the refresh_tokens table lean.
     *
     * Relies on @EnableScheduling being active (already in AsyncConfig.java).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = repository.deleteExpiredTokens(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens older than {}", deleted, cutoff);
        }
    }
}
