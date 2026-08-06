// src/main/java/Crowdspark/Crowdspark/repository/DonationRepository.java
// CHANGE: Added findByProject_IdAndPaymentStatus to fetch all backers of a project
//         (used by scheduler to notify backers when campaign is FUNDED or FAILED)

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    // BUG FIX (Feature #1/#4): verifyAndConfirm() (client callback) and
    // confirmFromWebhook() (Razorpay server callback) both normally fire for
    // the same donation within moments of each other -- that's the whole
    // point of having the webhook as a safety net. With a plain findById(),
    // both transactions could read PaymentStatus.PENDING before either
    // commits, and BOTH would then credit project.currentAmount, double-email
    // the receipt, and double-count backer/creator stats. These two locked
    // lookups take a DB row lock (SELECT ... FOR UPDATE) so the second caller
    // blocks until the first commits, then sees PaymentStatus.SUCCESS and
    // safely no-ops via the existing duplicate-check in confirmDonationPaid.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Donation d WHERE d.id = :id")
    Optional<Donation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Donation d WHERE d.razorpayOrderId = :orderId")
    Optional<Donation> findByRazorpayOrderIdForUpdate(@Param("orderId") String orderId);

    // FIX #10: backs the receipt download endpoint. Fetch-joins backer/project/
    // creator/rewardTier in one query so PaymentServiceImpl can pull every field
    // it needs without extra lazy-load round trips.
    @Query("SELECT d FROM Donation d " +
            "JOIN FETCH d.backer " +
            "JOIN FETCH d.project p " +
            "JOIN FETCH p.creator " +
            "LEFT JOIN FETCH d.rewardTier " +
            "WHERE d.id = :id")
    Optional<Donation> findDetailedById(@Param("id") Long id);

    // BUG FIX (Feature #33): both feed DonationServiceImpl.toResponse(), which
    // reads d.getProject() and d.getBacker() for every donation in the list —
    // plain derived queries + LAZY associations meant one extra SELECT per
    // donation for each. JOIN FETCH pulls both in the same query (safe to
    // combine two to-one associations, unlike two to-many collections).
    @Query("SELECT d FROM Donation d JOIN FETCH d.project JOIN FETCH d.backer WHERE d.backer.id = :backerId ORDER BY d.createdAt DESC")
    List<Donation> findByBacker_IdOrderByCreatedAtDesc(@Param("backerId") Long backerId);

    @Query("SELECT d FROM Donation d JOIN FETCH d.project JOIN FETCH d.backer WHERE d.project.id = :projectId ORDER BY d.createdAt DESC")
    List<Donation> findByProject_IdOrderByCreatedAtDesc(@Param("projectId") Long projectId);

    long countByProject_IdAndPaymentStatus(Long projectId, PaymentStatus status);

    long countByBacker_IdAndPaymentStatus(Long backerId, PaymentStatus status);

    // ── NEW: fetch all successful donations for a project (used by scheduler) ─
    List<Donation> findByProject_IdAndPaymentStatus(Long projectId, PaymentStatus status);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.project.id = :projectId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByProject(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.backer.id = :userId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByBacker(@Param("userId") Long userId);


    boolean existsByBacker_IdAndProject_IdAndPaymentStatus(Long backerId, Long projectId,
                                                           PaymentStatus status);

    // AUDIT FIX (Feature #18): backs FollowServiceImpl's followed-feed
    // CreatorDto, which previously hardcoded totalBackers to 0 instead of
    // computing it. Batched by creator ID (rather than one query per project)
    // for the same N+1 reasons as the other AUDIT FIX batched queries.
    @Query("SELECT d.project.creator.id, COUNT(DISTINCT d.backer.id) FROM Donation d " +
           "WHERE d.project.creator.id IN :creatorIds AND d.paymentStatus = 'SUCCESS' " +
           "GROUP BY d.project.creator.id")
    List<Object[]> countDistinctBackersByCreatorIds(@Param("creatorIds") List<Long> creatorIds);

    // AUDIT FIX (Feature #1/#4): the Razorpay webhook only knows Razorpay's own
    // order_id (it has no idea what our internal donation ID is), so the webhook
    // handler needs to look the donation up by that instead.
    Optional<Donation> findByRazorpayOrderId(String razorpayOrderId);
}