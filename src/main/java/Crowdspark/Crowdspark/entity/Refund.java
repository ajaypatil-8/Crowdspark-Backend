// src/main/java/Crowdspark/Crowdspark/entity/Refund.java
package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.RefundStatus;
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
@Table(name = "refunds",
        indexes = {
                @Index(name = "idx_refunds_donation_id", columnList = "donation_id"),
                @Index(name = "idx_refunds_backer_id",   columnList = "backer_id"),
                @Index(name = "idx_refunds_project_id",  columnList = "project_id"),
                @Index(name = "idx_refunds_status",      columnList = "status")
        })
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backer_id", nullable = false)
    private User backer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status = RefundStatus.INITIATED;

    private String razorpayRefundId;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;
}
