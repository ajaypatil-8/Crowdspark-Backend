// src/main/java/Crowdspark/Crowdspark/controller/RewardClaimController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.RewardClaimResponse;
import Crowdspark.Crowdspark.dto.RewardClaimShippingRequest;
import Crowdspark.Crowdspark.dto.RewardClaimStatusRequest;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.RewardClaimService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reward Claims", description = "Fulfillment tracking for reward-tier backers")
public class RewardClaimController {

    private final RewardClaimService claimService;
    private final UserService        userService;

    // ── Creator: project claims ───────────────────────────────────────────

    @Operation(
        summary  = "List all reward claims for a project",
        description = "Creator only. Paginated, optionally filtered by status.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/api/v1/projects/{projectId}/reward-claims")
    public ResponseEntity<ApiResponse<Page<RewardClaimResponse>>> getProjectClaims(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.getProjectClaims(projectId, creator.getId(), status, page, size)));
    }

    @Operation(
        summary  = "Reward claim status summary",
        description = "Count per status for a project — used in creator dashboard.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/api/v1/projects/{projectId}/reward-claims/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getClaimSummary(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.getProjectClaimSummary(projectId, creator.getId())));
    }

    // ── Creator: update status ────────────────────────────────────────────

    @Operation(
        summary  = "Update claim status",
        description = "Creator advances fulfillment status. SHIPPED requires a trackingNumber.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('CREATOR')")
    @PutMapping("/api/v1/reward-claims/{claimId}/status")
    public ResponseEntity<ApiResponse<RewardClaimResponse>> updateStatus(
            @PathVariable Long claimId,
            @Valid @RequestBody RewardClaimStatusRequest request,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.updateStatus(claimId, request, creator.getId())));
    }

    // ── Backer: my claims ─────────────────────────────────────────────────

    @Operation(
        summary  = "Get my reward claims",
        description = "Backer's own reward claims across all backed projects.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/v1/backer/reward-claims")
    public ResponseEntity<ApiResponse<List<RewardClaimResponse>>> myBackerClaims(
            @AuthenticationPrincipal String username) {

        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.getMyBackerClaims(backer.getId())));
    }

    // ── Backer: update shipping ───────────────────────────────────────────

    @Operation(
        summary  = "Submit / update shipping address",
        description = "Backer submits delivery address for physical rewards. " +
                      "Only allowed while status is PENDING or PROCESSING.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/api/v1/reward-claims/{claimId}/shipping")
    public ResponseEntity<ApiResponse<RewardClaimResponse>> updateShipping(
            @PathVariable Long claimId,
            @Valid @RequestBody RewardClaimShippingRequest request,
            @AuthenticationPrincipal String username) {

        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                claimService.updateShipping(claimId, request, backer.getId())));
    }
}
