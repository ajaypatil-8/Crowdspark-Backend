package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reward_tiers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double minimumAmount;

    // BUG FIX (Feature #24): these three were accepted and validated on
    // RewardTierRequest but had no entity fields (and no DB columns) to
    // land in — silently discarded on every create/update. Null
    // limitedQuantity/quantityAvailable means "unlimited", matching how
    // tiers behaved before this fix (no cap at all).

    /** Optional: creator-facing delivery estimate, e.g. "March 2026" */
    @Column(name = "estimated_delivery", length = 100)
    private String estimatedDelivery;

    /** Optional: total cap on how many backers can claim this tier. Null = unlimited. */
    @Column(name = "limited_quantity")
    private Integer limitedQuantity;

    /** Remaining claimable units. Null = unlimited. Decremented atomically on claim. */
    @Column(name = "quantity_available")
    private Integer quantityAvailable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
