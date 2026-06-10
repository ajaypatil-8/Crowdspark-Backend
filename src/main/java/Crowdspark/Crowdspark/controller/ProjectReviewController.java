// src/main/java/Crowdspark/Crowdspark/controller/ProjectReviewController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ProjectReviewRequest;
import Crowdspark.Crowdspark.dto.ProjectReviewResponse;
import Crowdspark.Crowdspark.dto.ReviewSummaryResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.ProjectReviewService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews & Ratings", description = "Star ratings and written reviews — only verified backers can submit")
public class ProjectReviewController {

    private final ProjectReviewService reviewService;
    private final UserService          userService;

    // ── GET /api/projects/{id}/reviews/summary ───────────────────────────────

    @Operation(summary = "Get rating summary",
               description = "Average rating, star distribution, and the caller's own review if any.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getSummary(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {

        Long userId = resolveUserId(username);
        return ResponseEntity.ok(
                ApiResponse.ok(reviewService.getSummary(projectId, userId)));
    }

    // ── GET /api/projects/{id}/reviews ──────────────────────────────────────

    @Operation(summary = "Get paginated reviews",
               description = "Newest first. myReview flag set when authenticated.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectReviewResponse>>> getReviews(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal String username) {

        Long userId = resolveUserId(username);
        return ResponseEntity.ok(
                ApiResponse.ok(reviewService.getReviews(projectId, page, size, userId)));
    }

    // ── POST /api/projects/{id}/reviews ─────────────────────────────────────

    @Operation(summary = "Submit a review",
               description = "Only backers with a successful donation can review. One review per project.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectReviewResponse>> submitReview(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectReviewRequest request,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        ProjectReviewResponse response =
                reviewService.submitReview(projectId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // ── PUT /api/projects/{id}/reviews/{reviewId} ────────────────────────────

    @Operation(summary = "Update your review",
               description = "Reviewer can update their own review at any time.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ProjectReviewResponse>> updateReview(
            @PathVariable Long projectId,
            @PathVariable Long reviewId,
            @Valid @RequestBody ProjectReviewRequest request,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        return ResponseEntity.ok(
                ApiResponse.ok(reviewService.updateReview(projectId, reviewId, request, user.getId())));
    }

    // ── DELETE /api/projects/{id}/reviews/{reviewId} ─────────────────────────

    @Operation(summary = "Delete a review",
               description = "Reviewer or admin can delete.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long projectId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        reviewService.deleteReview(projectId, reviewId, user.getId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true).message("Review deleted").build());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Returns null for unauthenticated callers — used for optional auth endpoints. */
    private Long resolveUserId(String username) {
        if (username == null) return null;
        try {
            return userService.getByUsername(username).getId();
        } catch (Exception e) {
            return null;
        }
    }
}
