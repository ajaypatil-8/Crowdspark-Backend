// src/main/java/Crowdspark/Crowdspark/entity/RefreshToken.java
// Feature #28 — Refresh Token Rotation Security
//
// New fields added:
//   familyId        — shared UUID for every token in one login session chain
//   parentTokenHash — SHA-256 of the previous token (null for first token)
//   createdAt       — when this token was issued

package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
    @Index(name = "idx_refresh_tokens_user_id",   columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_expiry",     columnList = "expiry_date")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hash of the raw token sent to the client — never store raw. */
    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    private boolean revoked = false;

    // ── Feature #28: family tracking ──────────────────────────────────────────

    /**
     * All tokens generated from the same login session share this UUID.
     * When a revoked token in a family is replayed, every token in the family
     * is immediately invalidated (theft response).
     */
    @Column(name = "family_id", length = 36)
    private String familyId;

    /**
     * SHA-256 hash of the token that was rotated to produce this one.
     * Null for the first token in a family (generated at login).
     * Used for audit-trail chain reconstruction.
     */
    @Column(name = "parent_token_hash", length = 64)
    private String parentTokenHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
