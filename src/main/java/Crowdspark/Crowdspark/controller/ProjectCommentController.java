// src/main/java/Crowdspark/Crowdspark/controller/ProjectCommentController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ProjectCommentRequest;
import Crowdspark.Crowdspark.dto.ProjectCommentResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.ProjectCommentService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/comments")
@RequiredArgsConstructor
public class ProjectCommentController {

    private final ProjectCommentService commentService;
    private final UserService           userService;

    /**
     * GET /api/projects/{projectId}/comments?page=0&size=20
     * Public — paginated top-level comments with their replies.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectCommentResponse>>> getComments(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(commentService.getComments(projectId, page, size)));
    }

    /**
     * POST /api/projects/{projectId}/comments
     * Authenticated — post a comment or reply.
     * Body: { content: "...", parentCommentId: null|123 }
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCommentResponse>> postComment(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectCommentRequest request,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        ProjectCommentResponse response =
                commentService.postComment(projectId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * DELETE /api/projects/{projectId}/comments/{commentId}
     * Author or project creator can soft-delete a comment.
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long projectId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        commentService.deleteComment(projectId, commentId, user.getId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Comment deleted").build());
    }
}
