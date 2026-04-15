package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.service.RewardTierService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/rewards")
@RequiredArgsConstructor
public class RewardTierController {

    private final RewardTierService rewardTierService;
    private final UserService userService;

    @GetMapping
    public List<RewardTierResponse> getRewards(@PathVariable Long projectId) {
        return rewardTierService.getByProject(projectId);
    }

    @PostMapping
    public ResponseEntity<RewardTierResponse> addReward(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username,
            @Valid @RequestBody RewardTierRequest req) {
        Long creatorId = userService.getByUsername(username).getId();
        return ResponseEntity.ok(rewardTierService.add(projectId, creatorId, req));
    }

    @PutMapping("/{tierId}")
    public ResponseEntity<RewardTierResponse> updateReward(
            @PathVariable Long projectId,
            @PathVariable Long tierId,
            @AuthenticationPrincipal String username,
            @Valid @RequestBody RewardTierRequest req) {
        Long creatorId = userService.getByUsername(username).getId();
        return ResponseEntity.ok(rewardTierService.update(tierId, creatorId, req));
    }

    @DeleteMapping("/{tierId}")
    public ResponseEntity<Void> deleteReward(
            @PathVariable Long projectId,
            @PathVariable Long tierId,
            @AuthenticationPrincipal String username) {
        Long creatorId = userService.getByUsername(username).getId();
        rewardTierService.delete(tierId, creatorId);
        return ResponseEntity.noContent().build();
    }
}