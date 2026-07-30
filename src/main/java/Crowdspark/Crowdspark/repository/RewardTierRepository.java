package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.RewardTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RewardTierRepository extends JpaRepository<RewardTier, Long> {
    List<RewardTier> findByProject_Id(Long projectId);

    // BUG FIX (Feature #24): atomic, race-condition-safe inventory tracking
    // for limited-quantity tiers. The WHERE clause makes this a single
    // conditional UPDATE at the database level rather than a read-then-write
    // from Java, so two concurrent claims for the last unit can't both
    // succeed — only one UPDATE will actually affect a row (return 1); the
    // other returns 0, which callers use to detect "sold out" without a
    // separate lock.

    /** Returns 1 if a unit was successfully reserved, 0 if none were left. */
    @Modifying
    @Query("UPDATE RewardTier t SET t.quantityAvailable = t.quantityAvailable - 1 " +
           "WHERE t.id = :tierId AND t.quantityAvailable > 0")
    int decrementQuantityIfAvailable(@Param("tierId") Long tierId);

    /** Gives a unit back — called when a claim is cancelled (e.g. a refund). Only applies to tiers that actually have a limit. */
    @Modifying
    @Query("UPDATE RewardTier t SET t.quantityAvailable = t.quantityAvailable + 1 " +
           "WHERE t.id = :tierId AND t.limitedQuantity IS NOT NULL " +
           "AND t.quantityAvailable < t.limitedQuantity")
    void incrementQuantity(@Param("tierId") Long tierId);
}
