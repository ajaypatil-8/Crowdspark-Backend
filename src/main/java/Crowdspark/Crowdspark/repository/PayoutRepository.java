// src/main/java/Crowdspark/Crowdspark/repository/PayoutRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Payout;
import Crowdspark.Crowdspark.entity.type.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Optional<Payout> findByProject_Id(Long projectId);

    boolean existsByProject_Id(Long projectId);

    List<Payout> findAllByOrderByInitiatedAtDesc();

    List<Payout> findByStatusOrderByInitiatedAtDesc(PayoutStatus status);
}
