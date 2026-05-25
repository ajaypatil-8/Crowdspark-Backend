// src/main/java/Crowdspark/Crowdspark/entity/Donation.java
// CHANGE: Added `razorpayOrderId` field — only this field is new.
// Everything else is identical to your existing file.

package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "donations",
        indexes = {
                @Index(name = "idx_donations_user_id",           columnList = "user_id"),
                @Index(name = "idx_donations_project_id",        columnList = "project_id"),
                @Index(name = "idx_donations_razorpay_order_id", columnList = "razorpay_order_id")
        })
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User backer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Double amount;

    /** Optional reward tier backer chose (null = no reward) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_tier_id")
    private RewardTier rewardTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    // ── NEW: Razorpay order id (set when order is created, before payment) ───
    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    /** Razorpay payment id — set after successful payment verification */
    private String transactionId;

    /** Backer's note / message to creator (optional) */
    @Column(length = 500)
    private String message;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
}
