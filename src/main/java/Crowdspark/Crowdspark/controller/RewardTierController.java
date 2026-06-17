package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.service.RewardTierService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/rewards")
@RequiredArgsConstructor
@Tag(name = "Reward Tiers", description = "Campaign reward tiers")
public class RewardTierController {

    private final RewardTierService rewardTierService;
    private final UserService userService;

    @Operation(summary = "Get reward tiers for a project",
            description = "Public endpoint. Returns all reward tiers for a given project.")
    @GetMapping
    public List<RewardTierResponse> getRewards(@PathVariable Long projectId) {
        return rewardTierService.getByProject(projectId);
    }

    @Operation(summary = "Add a reward tier to a project",
            description = "Creator only. Adds a new reward tier to the specified project.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<RewardTierResponse> addReward(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username,
            @Valid @RequestBody RewardTierRequest req) {
        Long creatorId = userService.getByUsername(username).getId();
        return ResponseEntity.ok(rewardTierService.add(projectId, creatorId, req));
    }

    @Operation(summary = "Update a reward tier",
            description = "Creator only. Updates an existing reward tier by tier ID.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{tierId}")
    public ResponseEntity<RewardTierResponse> updateReward(
            @PathVariable Long projectId,
            @PathVariable Long tierId,
            @AuthenticationPrincipal String username,
            @Valid @RequestBody RewardTierRequest req) {
        Long creatorId = userService.getByUsername(username).getId();
        return ResponseEntity.ok(rewardTierService.update(tierId, creatorId, req));
    }

    @Operation(summary = "Delete a reward tier",
            description = "Creator only. Deletes a reward tier by tier ID.",
            security = @SecurityRequirement(name = "bearerAuth"))
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