// src/main/java/Crowdspark/Crowdspark/entity/KycDocumentAiCheck.java
// Feature #44 — AI KYC Document Validation

package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.KycCheckStatus;
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
@Table(name = "kyc_document_ai_checks",
        indexes = {
                @Index(name = "idx_kyc_ai_checks_kyc_document_id", columnList = "kyc_document_id")
        })
public class KycDocumentAiCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_document_id", nullable = false, unique = true)
    private KycDocument kycDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycCheckStatus status = KycCheckStatus.PENDING;

    /** null until status = COMPLETED. */
    private Boolean readable;

    /** null until status = COMPLETED. */
    private Boolean tamperingSuspected;

    /** Newline-separated specific quality/authenticity observations — never
     *  the ID number, name, address, or DOB itself (enforced by the system
     *  prompt, see AiServiceImpl.KYC_SYSTEM_PROMPT). */
    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String model;
    private LocalDateTime checkedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
