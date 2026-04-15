package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.service.RewardTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor
public class RewardTierServiceImpl implements RewardTierService {

    private final RewardTierRepository rewardTierRepository;
    private final ProjectRepository projectRepository;

    @Override
    public List<RewardTierResponse> getByProject(Long projectId) {
        return rewardTierRepository.findByProject_Id(projectId).stream().map(this::toResponse).toList();
    }

    @Override
    public RewardTierResponse add(Long projectId, Long creatorId, RewardTierRequest req) {
        Project project = getProjectAndVerifyOwner(projectId, creatorId);
        RewardTier tier = RewardTier.builder()
            .title(req.getTitle()).description(req.getDescription())
            .minimumAmount(req.getMinimumAmount()).project(project).build();
        return toResponse(rewardTierRepository.save(tier));
    }

    @Override
    public RewardTierResponse update(Long tierId, Long creatorId, RewardTierRequest req) {
        RewardTier tier = getTierAndVerifyOwner(tierId, creatorId);
        tier.setTitle(req.getTitle());
        tier.setDescription(req.getDescription());
        tier.setMinimumAmount(req.getMinimumAmount());
        return toResponse(rewardTierRepository.save(tier));
    }

    @Override
    public void delete(Long tierId, Long creatorId) {
        RewardTier tier = getTierAndVerifyOwner(tierId, creatorId);
        rewardTierRepository.delete(tier);
    }

    private Project getProjectAndVerifyOwner(Long projectId, Long creatorId) {
        Project p = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!p.getCreator().getId().equals(creatorId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your project");
        return p;
    }

    private RewardTier getTierAndVerifyOwner(Long tierId, Long creatorId) {
        RewardTier t = rewardTierRepository.findById(tierId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tier not found"));
        if (!t.getProject().getCreator().getId().equals(creatorId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your tier");
        return t;
    }

    private RewardTierResponse toResponse(RewardTier t) {
        return RewardTierResponse.builder().id(t.getId()).title(t.getTitle())
            .description(t.getDescription()).minimumAmount(t.getMinimumAmount()).build();
    }
}