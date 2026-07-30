// src/main/java/Crowdspark/Crowdspark/repository/RewardClaimRepository.java

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.RewardClaim;
import Crowdspark.Crowdspark.entity.type.RewardClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RewardClaimRepository extends JpaRepository<RewardClaim, Long> {

    /** All claims for a project — creator view, newest first */
    Page<RewardClaim> findByProject_IdOrderByClaimedAtDesc(Long projectId, Pageable pageable);

    /** Filtered by status — for creator to see e.g. only PENDING */
    Page<RewardClaim> findByProject_IdAndStatusOrderByClaimedAtDesc(
            Long projectId, RewardClaimStatus status, Pageable pageable);

    /** All claims for a backer across all projects */
    List<RewardClaim> findByBacker_IdOrderByClaimedAtDesc(Long backerId);

    Optional<RewardClaim> findByDonation_Id(Long donationId);

    boolean existsByDonation_Id(Long donationId);

    /** Count by status for a project — used in dashboard summary */
    long countByProject_IdAndStatus(Long projectId, RewardClaimStatus status);

    /** Summary counts per status for a project */
    @Query("SELECT c.status, COUNT(c) FROM RewardClaim c " +
           "WHERE c.project.id = :projectId GROUP BY c.status")
    List<Object[]> countByStatusForProject(@Param("projectId") Long projectId);

    /**
     * Non-cancelled claims against a tier — used when a creator changes
     * limitedQuantity on an existing tier, so quantityAvailable is
     * recomputed against what's actually been claimed rather than drifting
     * via repeated deltas.
     */
    long countByRewardTier_IdAndStatusNot(Long rewardTierId, RewardClaimStatus excludedStatus);
}
