// src/main/java/Crowdspark/Crowdspark/repository/RefreshTokenRepository.java
// Feature #28 — Refresh Token Rotation Security
//
// New queries added:
//   revokeAllByFamilyId  — hot path on theft detection; revokes every token
//                          in the same login-session chain in a single UPDATE
//   existsActiveByFamilyId — quick check before issuing a new token in the family
//   deleteExpiredTokens  — cleanup helper for the scheduled purge job

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String tokenHash);

    // ── Existing: per-user revocation (logout / account delete) ──────────────

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);

    // ── Feature #28: family-wide revocation (theft response) ─────────────────

    /**
     * Revokes every token that belongs to the given family UUID.
     * Called instantly when token-theft is detected (a revoked token replayed).
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.familyId = :familyId")
    void revokeAllByFamilyId(@Param("familyId") String familyId);

    /**
     * Returns all tokens in a family — used for audit logging / forensics.
     */
    List<RefreshToken> findAllByFamilyIdOrderByCreatedAtAsc(String familyId);

    /**
     * Checks whether a family has any active (non-revoked, non-expired) tokens.
     * Used to reject rotation attempts on a fully revoked family.
     */
    @Query("""
        SELECT COUNT(r) > 0
        FROM RefreshToken r
        WHERE r.familyId = :familyId
          AND r.revoked  = false
          AND r.expiryDate > :now
    """)
    boolean existsActiveByFamilyId(@Param("familyId") String familyId,
                                   @Param("now")       LocalDateTime now);

    // ── Maintenance: expired-token cleanup ────────────────────────────────────

    /**
     * Hard-deletes tokens that expired before the given cutoff.
     * Called by the nightly @Scheduled cleanup job in RefreshTokenServiceImpl.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}
