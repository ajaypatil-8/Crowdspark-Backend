// src/main/java/Crowdspark/Crowdspark/repository/CampaignUpdateRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.CampaignUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignUpdateRepository extends JpaRepository<CampaignUpdate, Long> {

    List<CampaignUpdate> findByProject_IdOrderByCreatedAtDesc(Long projectId);

    long countByProject_Id(Long projectId);

    Optional<CampaignUpdate> findByIdAndProject_Id(Long id, Long projectId);
}
