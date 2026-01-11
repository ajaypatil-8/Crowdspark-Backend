package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateRewardRequest;
import Crowdspark.Crowdspark.dto.RewardResponse;
import Crowdspark.Crowdspark.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/rewards")
@RequiredArgsConstructor
@Validated
public class RewardController {

    private static final Logger logger = LoggerFactory.getLogger(RewardController.class);

    private final RewardService rewardService;

    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping
    public ResponseEntity<RewardResponse> createReward(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateRewardRequest request
    ) {
        RewardResponse response = rewardService.createReward(projectId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("Reward created: id={}, projectId={}, minAmount={}", response.getId(), response.getProjectId(), response.getMinAmount());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RewardResponse>> getRewards(@PathVariable Long projectId) {
        List<RewardResponse> rewards = rewardService.getProjectRewards(projectId);
        return ResponseEntity.ok(rewards);
    }
}
