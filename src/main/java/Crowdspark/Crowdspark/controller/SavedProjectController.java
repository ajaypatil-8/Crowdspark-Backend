// src/main/java/Crowdspark/Crowdspark/controller/SavedProjectController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.SavedProjectService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/saved")
@RequiredArgsConstructor
public class SavedProjectController {

    private final SavedProjectService savedProjectService;
    private final UserService         userService;

    /**
     * GET /api/users/saved
     * Returns all projects saved by the logged-in user, newest first.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectFeedResponse>>> getSaved(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(
                ApiResponse.ok(savedProjectService.getSaved(user.getId())));
    }

    /**
     * GET /api/users/saved/{projectId}/check
     * Returns { saved: true/false } — used to set the bookmark button state.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{projectId}/check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkSaved(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        boolean saved = savedProjectService.isSaved(user.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", saved)));
    }

    /**
     * POST /api/users/saved/{projectId}
     * Save a project. Idempotent — safe to call if already saved.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> saveProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        savedProjectService.save(user.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Project saved").build());
    }

    /**
     * DELETE /api/users/saved/{projectId}
     * Unsave a project. Idempotent — safe to call if not saved.
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> unsaveProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        savedProjectService.unsave(user.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Project removed from saved").build());
    }

    /**
     * PUT /api/users/saved/{projectId}/toggle
     * Toggle save state. Returns { saved: true/false } for the new state.
     * Used by the bookmark button on the project detail page.
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{projectId}/toggle")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleSaved(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        boolean nowSaved = savedProjectService.toggle(user.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.ok(
                nowSaved ? "Project saved" : "Project removed from saved",
                Map.of("saved", nowSaved)));
    }
}
