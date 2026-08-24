// src/main/java/Crowdspark/Crowdspark/repository/ProjectFraudCheckRepository.java
// Feature #43 — AI Fraud & Risk Detection

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectFraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFraudCheckRepository extends JpaRepository<ProjectFraudCheck, Long> {

    Optional<ProjectFraudCheck> findByProject_Id(Long projectId);

    // Batch lookup for the admin queue list — avoids N+1 across a page of
    // pending projects (same reasoning as every other batch-fetch method
    // already in this codebase, e.g. donationRepository.countDistinctBackersByCreatorIds).
    List<ProjectFraudCheck> findByProject_IdIn(List<Long> projectIds);
}
