// src/main/java/Crowdspark/Crowdspark/repository/CampaignWeeklyInsightRepository.java
// Feature #48 — AI Creator Analytics Insights

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.CampaignWeeklyInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignWeeklyInsightRepository extends JpaRepository<CampaignWeeklyInsight, Long> {

    /** Latest insight for one project — used when a single campaign page needs it. */
    Optional<CampaignWeeklyInsight> findTopByProject_IdOrderByWeekStartDesc(Long projectId);

    /** Latest insight per project across a creator's whole portfolio, for the
     *  dashboard widget — one batch query, filtered/deduped in the service. */
    List<CampaignWeeklyInsight> findByProject_IdInOrderByWeekStartDesc(List<Long> projectIds);
}
