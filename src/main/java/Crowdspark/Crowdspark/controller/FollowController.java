// src/main/java/Crowdspark/Crowdspark/controller/FollowController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.FollowResponse;
import Crowdspark.Crowdspark.dto.FollowStatusResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.FollowService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Follow / Social", description = "Follow creators and browse their project feed")
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserService   userService;

    // ── PUT /api/users/{id}/follow — toggle follow ────────────────────────────

    @Operation(summary = "Follow or unfollow a user",
               description = "Toggle — if already following, unfollows. Returns new state.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/api/users/{targetId}/follow")
    public ResponseEntity<ApiResponse<FollowStatusResponse>> toggleFollow(
            @PathVariable Long targetId,
            @AuthenticationPrincipal String username) {

        User me = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                followService.toggle(me.getId(), targetId)));
    }

    // ── GET /api/users/{id}/follow/status — check if following ───────────────

    @Operation(summary = "Check if you follow a user",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/users/{targetId}/follow/status")
    public ResponseEntity<ApiResponse<FollowStatusResponse>> checkFollowStatus(
            @PathVariable Long targetId,
            @AuthenticationPrincipal String username) {

        User me = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                followService.checkStatus(me.getId(), targetId)));
    }

    // ── GET /api/users/{id}/following — who they follow ──────────────────────

    @Operation(summary = "Get list of users this person follows")
    @GetMapping("/api/users/{userId}/following")
    public ResponseEntity<ApiResponse<Page<FollowResponse>>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.ok(
                followService.getFollowing(userId, page, size)));
    }

    // ── GET /api/users/{id}/followers — who follows them ─────────────────────

    @Operation(summary = "Get list of followers for a user")
    @GetMapping("/api/users/{userId}/followers")
    public ResponseEntity<ApiResponse<Page<FollowResponse>>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.ok(
                followService.getFollowers(userId, page, size)));
    }

    // ── GET /api/feed/followed — projects from followed creators ─────────────

    @Operation(summary = "Get project feed from creators you follow",
               description = "Returns latest 20 APPROVED projects from followed creators.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/feed/followed")
    public ResponseEntity<ApiResponse<List<ProjectFeedResponse>>> getFollowedFeed(
            @AuthenticationPrincipal String username) {

        User me = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                followService.getFollowedFeed(me.getId())));
    }
}
