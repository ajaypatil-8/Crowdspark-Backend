// src/main/java/Crowdspark/Crowdspark/repository/DonationRepository.java
// CHANGE: Added findByProject_IdAndPaymentStatus to fetch all backers of a project
//         (used by scheduler to notify backers when campaign is FUNDED or FAILED)

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

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

    List<Donation> findByBacker_IdOrderByCreatedAtDesc(Long backerId);

    List<Donation> findByProject_IdOrderByCreatedAtDesc(Long projectId);

    long countByProject_IdAndPaymentStatus(Long projectId, PaymentStatus status);

    long countByBacker_IdAndPaymentStatus(Long backerId, PaymentStatus status);

    // ── NEW: fetch all successful donations for a project (used by scheduler) ─
    List<Donation> findByProject_IdAndPaymentStatus(Long projectId, PaymentStatus status);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.project.id = :projectId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByProject(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.backer.id = :userId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByBacker(@Param("userId") Long userId);

    boolean existsByBacker_IdAndProject_Id(Long backerId, Long projectId);
}
