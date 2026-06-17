// src/main/java/Crowdspark/Crowdspark/controller/ProjectMilestoneController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.MilestoneRequest;
import Crowdspark.Crowdspark.dto.MilestoneResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.ProjectMilestoneService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/milestones")
@RequiredArgsConstructor
@Tag(name = "Milestones", description = "Creator-managed project milestones with backer notifications on completion")
public class ProjectMilestoneController {

    private final ProjectMilestoneService milestoneService;
    private final UserService             userService;

    // ── GET /api/projects/{id}/milestones ────────────────────────────────────

    @Operation(summary = "Get all milestones", description = "Public. Returns milestones ordered by sort_order.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> getMilestones(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(
                ApiResponse.ok(milestoneService.getMilestones(projectId)));
    }

    // ── POST /api/projects/{id}/milestones ───────────────────────────────────

    @Operation(summary = "Create milestone", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping
    public ResponseEntity<ApiResponse<MilestoneResponse>> createMilestone(
            @PathVariable Long projectId,
            @Valid @RequestBody MilestoneRequest request,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        MilestoneResponse response =
                milestoneService.createMilestone(projectId, request, creator.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // ── PUT /api/projects/{id}/milestones/{milestoneId} ──────────────────────

    @Operation(summary = "Update milestone", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PutMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneRequest request,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                milestoneService.updateMilestone(projectId, milestoneId, request, creator.getId())));
    }

    // ── DELETE /api/projects/{id}/milestones/{milestoneId} ───────────────────

    @Operation(summary = "Delete milestone", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @DeleteMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<Void>> deleteMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        milestoneService.deleteMilestone(projectId, milestoneId, creator.getId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true).message("Milestone deleted").build());
    }

    // ── POST /api/projects/{id}/milestones/{milestoneId}/complete ────────────

    @Operation(
        summary = "Mark milestone complete",
        description = "Marks the milestone as completed and sends a notification to all backers.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/{milestoneId}/complete")
    public ResponseEntity<ApiResponse<MilestoneResponse>> completeMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                milestoneService.completeMilestone(projectId, milestoneId, creator.getId())));
    }

    // ── POST /api/projects/{id}/milestones/{milestoneId}/reopen ─────────────

    @Operation(
        summary = "Reopen milestone",
        description = "Reverts a completed milestone back to PENDING.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/{milestoneId}/reopen")
    public ResponseEntity<ApiResponse<MilestoneResponse>> reopenMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                milestoneService.reopenMilestone(projectId, milestoneId, creator.getId())));
    }
}
