package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    List<Reward> findByProjectIdOrderByMinAmountDesc(Long projectId);
}
