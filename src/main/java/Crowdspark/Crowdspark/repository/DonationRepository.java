package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByUserId(Long userId);

    List<Donation> findByProjectId(Long projectId);
}
