// src/main/java/Crowdspark/Crowdspark/controller/CampaignUpdateController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.CampaignUpdateRequest;
import Crowdspark.Crowdspark.dto.CampaignUpdateResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.CampaignUpdateService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/updates")
@RequiredArgsConstructor
public class CampaignUpdateController {

    private final CampaignUpdateService campaignUpdateService;
    private final UserService           userService;

    /**
     * GET /api/projects/{projectId}/updates
     * Public — anyone can view campaign updates.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CampaignUpdateResponse>>> getUpdates(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(
                ApiResponse.ok(campaignUpdateService.getUpdates(projectId)));
    }

    /**
     * POST /api/projects/{projectId}/updates
     * Creator only — post a new campaign update.
     */
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignUpdateResponse>> createUpdate(
            @PathVariable Long projectId,
            @Valid @RequestBody CampaignUpdateRequest request,
            @AuthenticationPrincipal String username) {
        User creator = userService.getByUsername(username);
        CampaignUpdateResponse response =
                campaignUpdateService.createUpdate(projectId, request, creator.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * PUT /api/projects/{projectId}/updates/{updateId}
     * Creator only — edit their own update.
     */
    @PreAuthorize("hasRole('CREATOR')")
    @PutMapping("/{updateId}")
    public ResponseEntity<ApiResponse<CampaignUpdateResponse>> editUpdate(
            @PathVariable Long projectId,
            @PathVariable Long updateId,
            @Valid @RequestBody CampaignUpdateRequest request,
            @AuthenticationPrincipal String username) {
        User creator = userService.getByUsername(username);
        CampaignUpdateResponse response =
                campaignUpdateService.editUpdate(projectId, updateId, request, creator.getId());
        return ResponseEntity.ok(ApiResponse.ok("Update edited", response));
    }

    /**
     * DELETE /api/projects/{projectId}/updates/{updateId}
     * Creator only — delete their own update.
     */
    @PreAuthorize("hasRole('CREATOR')")
    @DeleteMapping("/{updateId}")
    public ResponseEntity<ApiResponse<Void>> deleteUpdate(
            @PathVariable Long projectId,
            @PathVariable Long updateId,
            @AuthenticationPrincipal String username) {
        User creator = userService.getByUsername(username);
        campaignUpdateService.deleteUpdate(projectId, updateId, creator.getId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Update deleted").build());
    }
}
