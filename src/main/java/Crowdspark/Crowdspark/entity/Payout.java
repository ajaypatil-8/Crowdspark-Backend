// src/main/java/Crowdspark/Crowdspark/entity/Payout.java
package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.PayoutStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payouts",
    indexes = {
        @Index(name = "idx_payouts_project_id", columnList = "project_id"),
        @Index(name = "idx_payouts_creator_id", columnList = "creator_id"),
        @Index(name = "idx_payouts_status",     columnList = "status")
    })
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One payout per project (enforced by DB unique constraint) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    /** Total amount raised by the campaign (project.currentAmount) */
    @Column(nullable = false)
    private Double grossAmount;

    /** Platform fee percentage applied (e.g. 5.0 = 5%) */
    @Column(nullable = false)
    private Double platformFeePercent;

    /** Actual fee amount deducted */
    @Column(nullable = false)
    private Double platformFeeAmount;

    /** Amount transferred to creator = grossAmount - platformFeeAmount */
    @Column(nullable = false)
    private Double netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status = PayoutStatus.INITIATED;

    /** UPI or BANK_TRANSFER */
    @Column(nullable = false)
    private String payoutMode;

    /** Snapshot of creator's UPI ID at time of payout */
    private String upiIdSnapshot;

    /** Razorpay IDs — set after API calls */
    private String razorpayContactId;
    private String razorpayFundAccountId;
    private String razorpayPayoutId;

    /** Set when payout fails */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    /** Admin who triggered the payout */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_id")
    private User initiatedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;
}
