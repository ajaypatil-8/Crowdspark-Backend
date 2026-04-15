package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.RewardTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardTierRepository extends JpaRepository<RewardTier, Long> {
    List<RewardTier> findByProject_Id(Long projectId);
}
