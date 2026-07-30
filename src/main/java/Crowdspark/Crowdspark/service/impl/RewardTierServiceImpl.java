package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.entity.type.RewardClaimStatus;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardClaimRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.service.RewardTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Slf4j
@Service @RequiredArgsConstructor
public class RewardTierServiceImpl implements RewardTierService {

    private final RewardTierRepository rewardTierRepository;
    private final ProjectRepository projectRepository;
    // BUG FIX (Feature #24): needed to recompute quantityAvailable correctly
    // when a creator edits limitedQuantity on a tier that may already have
    // claims against it (see resolveQuantityAvailable below).
    private final RewardClaimRepository rewardClaimRepository;

    @Override
    public List<RewardTierResponse> getByProject(Long projectId) {
        return rewardTierRepository.findByProject_Id(projectId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public RewardTierResponse add(Long projectId, Long creatorId, RewardTierRequest req) {
        Project project = getProjectAndVerifyOwner(projectId, creatorId);
        RewardTier tier = RewardTier.builder()
            .title(req.getTitle()).description(req.getDescription())
            .minimumAmount(req.getMinimumAmount())
            .estimatedDelivery(req.getEstimatedDelivery())
            .limitedQuantity(req.getLimitedQuantity())
            // Brand new tier -> nothing claimed yet, so available = the full limit.
            .quantityAvailable(req.getLimitedQuantity())
            .project(project).build();
        return toResponse(rewardTierRepository.save(tier));
    }

    @Override
    @Transactional
    public RewardTierResponse update(Long tierId, Long creatorId, RewardTierRequest req) {
        RewardTier tier = getTierAndVerifyOwner(tierId, creatorId);
        tier.setTitle(req.getTitle());
        tier.setDescription(req.getDescription());
        tier.setMinimumAmount(req.getMinimumAmount());
        tier.setEstimatedDelivery(req.getEstimatedDelivery());
        tier.setQuantityAvailable(resolveQuantityAvailable(tier, req.getLimitedQuantity()));
        tier.setLimitedQuantity(req.getLimitedQuantity());
        return toResponse(rewardTierRepository.save(tier));
    }

    @Override
    @Transactional
    public void delete(Long tierId, Long creatorId) {
        RewardTier tier = getTierAndVerifyOwner(tierId, creatorId);
        rewardTierRepository.delete(tier);
    }

    // BUG FIX (Feature #24): when a creator changes limitedQuantity on a tier
    // that already has claims, quantityAvailable must be recomputed against
    // what's *actually been claimed* (limit - claimedCount), not just reset
    // to the new limit outright (which would ignore already-consumed units
    // and could let the tier be oversold) or delta-adjusted from the old
    // value (which can drift across repeated edits). This is only evaluated
    // when the limit is actually changing.
    private Integer resolveQuantityAvailable(RewardTier tier, Integer newLimit) {
        if (newLimit == null) {
            return null; // creator removed the cap entirely -> unlimited again
        }
        boolean unchanged = newLimit.equals(tier.getLimitedQuantity());
        if (unchanged && tier.getQuantityAvailable() != null) {
            return tier.getQuantityAvailable(); // nothing to recompute
        }
        long claimed = rewardClaimRepository.countByRewardTier_IdAndStatusNot(
                tier.getId(), RewardClaimStatus.CANCELLED);
        return (int) Math.max(0, newLimit - claimed);
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
        boolean soldOut = t.getLimitedQuantity() != null
                && t.getQuantityAvailable() != null
                && t.getQuantityAvailable() <= 0;
        return RewardTierResponse.builder().id(t.getId()).title(t.getTitle())
            .description(t.getDescription()).minimumAmount(t.getMinimumAmount())
            .estimatedDelivery(t.getEstimatedDelivery())
            .limitedQuantity(t.getLimitedQuantity())
            .quantityAvailable(t.getQuantityAvailable())
            .soldOut(soldOut)
            .build();
    }
}