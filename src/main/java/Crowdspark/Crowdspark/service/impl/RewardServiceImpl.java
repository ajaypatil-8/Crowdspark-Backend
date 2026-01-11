package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateRewardRequest;
import Crowdspark.Crowdspark.dto.RewardResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.Reward;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final RewardRepository rewardRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public RewardResponse createReward(Long projectId, CreateRewardRequest request) {

        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AuthException("Not authenticated");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if (username == null) throw new AuthException("Not authenticated");

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        if (!project.getCreatorId().equals(user.getId())) {
            throw new AuthException("Not your project");
        }

        Reward reward = new Reward();
        reward.setProjectId(projectId);
        reward.setTitle(request.getTitle());
        reward.setMinAmount(request.getMinAmount());
        reward.setDescription(request.getDescription());

        Reward saved = rewardRepository.save(reward);

        return RewardResponse.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .title(saved.getTitle())
                .minAmount(saved.getMinAmount())
                .description(saved.getDescription())
                .build();
    }

    @Override
    public List<RewardResponse> getProjectRewards(Long projectId) {
        return rewardRepository.findByProjectIdOrderByMinAmountDesc(projectId)
                .stream()
                .map(r -> RewardResponse.builder()
                        .id(r.getId())
                        .projectId(r.getProjectId())
                        .title(r.getTitle())
                        .minAmount(r.getMinAmount())
                        .description(r.getDescription())
                        .build())
                .collect(Collectors.toList());
    }
}
