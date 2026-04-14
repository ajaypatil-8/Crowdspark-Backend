package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByBacker_IdOrderByCreatedAtDesc(Long backerId);

    List<Donation> findByProject_IdOrderByCreatedAtDesc(Long projectId);

    long countByProject_IdAndPaymentStatus(Long projectId, PaymentStatus status);

    long countByBacker_IdAndPaymentStatus(Long backerId, PaymentStatus status);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.project.id = :projectId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByProject(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.backer.id = :userId AND d.paymentStatus = 'SUCCESS'")
    Double sumSuccessfulByBacker(@Param("userId") Long userId);

    boolean existsByBacker_IdAndProject_Id(Long backerId, Long projectId);
}
