package Crowdspark.Crowdspark.service;
import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import java.util.List;

public interface RewardTierService {
    List<RewardTierResponse> getByProject(Long projectId);
    RewardTierResponse add(Long projectId, Long creatorId, RewardTierRequest req);
    RewardTierResponse update(Long tierId, Long creatorId, RewardTierRequest req);
    void delete(Long tierId, Long creatorId);
}