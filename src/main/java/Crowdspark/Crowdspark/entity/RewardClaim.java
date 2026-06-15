// src/main/java/Crowdspark/Crowdspark/entity/RewardClaim.java

package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.RewardClaimStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "reward_claims",
    indexes = {
        @Index(name = "idx_reward_claims_backer_id",  columnList = "backer_id"),
        @Index(name = "idx_reward_claims_project_id", columnList = "project_id"),
        @Index(name = "idx_reward_claims_tier_id",    columnList = "reward_tier_id"),
        @Index(name = "idx_reward_claims_status",     columnList = "project_id, status")
    }
)
public class RewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One claim per donation — enforced by UNIQUE on donation_id */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", nullable = false, unique = true)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backer_id", nullable = false)
    private User backer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_tier_id", nullable = false)
    private RewardTier rewardTier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardClaimStatus status = RewardClaimStatus.PENDING;

    // ── Backer shipping details ────────────────────────────────────────────
    @Column(name = "shipping_name",    length = 255)
    private String shippingName;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "shipping_city",    length = 100)
    private String shippingCity;

    @Column(name = "shipping_pincode", length = 20)
    private String shippingPincode;

    @Column(name = "shipping_country", length = 100)
    private String shippingCountry = "India";

    @Column(name = "shipping_phone",   length = 20)
    private String shippingPhone;

    // ── Creator fulfillment details ───────────────────────────────────────
    @Column(name = "tracking_number", length = 255)
    private String trackingNumber;

    @Column(name = "fulfillment_note", columnDefinition = "TEXT")
    private String fulfillmentNote;

    @CreatedDate
    @Column(name = "claimed_at", nullable = false, updatable = false)
    private LocalDateTime claimedAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;
}
