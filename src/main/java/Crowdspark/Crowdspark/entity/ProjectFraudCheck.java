// src/main/java/Crowdspark/Crowdspark/entity/ProjectFraudCheck.java
// Feature #43 — AI Fraud & Risk Detection

package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.FraudCheckStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "project_fraud_checks",
        indexes = {
                @Index(name = "idx_fraud_checks_project_id_entity", columnList = "project_id")
        })
public class ProjectFraudCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudCheckStatus status = FraudCheckStatus.PENDING;

    /** 0-100, null until status = COMPLETED. */
    private Integer riskScore;

    /** LOW / MEDIUM / HIGH, null until status = COMPLETED. */
    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /** Newline-separated specific flagged concerns; empty string is valid (nothing notable). */
    @Column(columnDefinition = "TEXT")
    private String signals;

    private String model;
    private LocalDateTime checkedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
