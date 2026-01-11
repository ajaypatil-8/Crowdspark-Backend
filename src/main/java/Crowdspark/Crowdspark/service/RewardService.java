package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateRewardRequest;
import Crowdspark.Crowdspark.dto.RewardResponse;

import java.util.List;

public interface RewardService {

    RewardResponse createReward(Long projectId, CreateRewardRequest request);

    List<RewardResponse> getProjectRewards(Long projectId);
}
