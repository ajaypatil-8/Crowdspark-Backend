// src/main/java/Crowdspark/Crowdspark/repository/RefundRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Refund;
import Crowdspark.Crowdspark.entity.type.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByProject_IdOrderByInitiatedAtDesc(Long projectId);

    List<Refund> findByBacker_IdOrderByInitiatedAtDesc(Long backerId);

    List<Refund> findByStatusOrderByInitiatedAtDesc(RefundStatus status);

    boolean existsByDonation_Id(Long donationId);
}
