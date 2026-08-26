// src/main/java/Crowdspark/Crowdspark/repository/KycDocumentAiCheckRepository.java
// Feature #44 — AI KYC Document Validation

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.KycDocumentAiCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentAiCheckRepository extends JpaRepository<KycDocumentAiCheck, Long> {

    Optional<KycDocumentAiCheck> findByKycDocument_Id(Long kycDocumentId);

    // Batch lookup for the admin KYC queue — avoids N+1 across a page of
    // pending submissions, same reasoning as ProjectFraudCheckRepository.
    List<KycDocumentAiCheck> findByKycDocument_IdIn(List<Long> kycDocumentIds);
}
