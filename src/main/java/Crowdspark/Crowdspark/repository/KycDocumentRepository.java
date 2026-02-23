package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.KycDocument;
import Crowdspark.Crowdspark.entity.type.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    Optional<KycDocument> findByUserId(Long userId);

    List<KycDocument> findByKycStatus(KycStatus kycStatus);

    boolean existsByUserId(Long userId);
}