package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.KycStatus;
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
@Table(name = "kyc_documents")
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private Long userId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING_SUBMISSION;

    private String rejectionReason;


    private String panNumber;
    private String panCardImageUrl;
    private String panCardImagePublicId;


    private String aadhaarNumber;
    private String aadhaarFrontImageUrl;
    private String aadhaarFrontPublicId;
    private String aadhaarBackImageUrl;
    private String aadhaarBackPublicId;


    private String bankAccountHolderName;
    private String bankAccountNumber;
    private String bankIfscCode;
    private String bankName;
    private String bankBranchName;


    private String upiId;


    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
    private Long reviewedByAdminId;
}