// src/main/java/Crowdspark/Crowdspark/service/RefreshTokenService.java
// Feature #28 — Refresh Token Rotation Security
//
// Interface additions:
//   create(userId, familyId, parentHash) — rotation overload (keeps family)
//   revokeFamily(familyId)               — terminate all tokens in a session chain
//   revokeByHash(tokenHash)              — internal revoke using the stored hash
//                                          (fixes the double-hash bug in AuthController)

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.RefreshToken;

public interface RefreshTokenService {

    /**
     * Creates the FIRST token in a new family (called at login / OAuth2 callback).
     * Generates a fresh random familyId UUID.
     */
    RefreshToken create(Long userId);

    /**
     * Creates the NEXT token in an existing family (called during token rotation).
     *
     * @param userId          owner of the session
     * @param familyId        UUID shared by all tokens in this login session
     * @param parentTokenHash SHA-256 of the token being replaced (for audit trail)
     */
    RefreshToken create(Long userId, String familyId, String parentTokenHash);

    /**
     * Validates the raw refresh token sent by the client.
     *
     * THEFT DETECTION (Feature #28):
     * If the token exists in the DB but is already REVOKED and belongs to a known
     * family, this is a replay of a previously-rotated token — a strong signal
     * of theft. The method immediately revokes ALL tokens in that family and
     * throws AuthException with a security-alert message.
     *
     * @param rawToken the token string received from the HTTP request
     * @return the valid RefreshToken entity (with hashed token in .getToken())
     * @throws Crowdspark.Crowdspark.exception.AuthException on invalid/expired/theft
     */
    RefreshToken validate(String rawToken);

    /**
     * Revokes a single token by its RAW value (hashes internally).
     * Use this in the controller where you have the original raw token.
     */
    void revoke(String rawToken);

    /**
     * Revokes a single token by its STORED HASH.
     * Used internally — avoids the double-hash bug that existed in the old code
     * when validate() returned the entity (with hash) and the caller passed
     * entity.getToken() to revoke().
     */
    void revokeByHash(String tokenHash);

    /**
     * Revokes every token in a family.
     * Called automatically by validate() on theft detection, but also exposed
     * so controllers can forcibly end a specific session chain (e.g. "sign out
     * all devices" partial implementation).
     */
    void revokeFamily(String familyId);

    /**
     * Revokes ALL tokens for a user across ALL families.
     * Called on: logout-all, password change, account deletion, forced sign-out.
     */
    void revokeAll(Long userId);
}
