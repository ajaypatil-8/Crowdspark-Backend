package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AddCommentRequest;
import Crowdspark.Crowdspark.dto.CommentResponse;
import Crowdspark.Crowdspark.service.CommentService;
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
@RequestMapping("/projects/{projectId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;


    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long projectId,
            @Valid @RequestBody AddCommentRequest request
    ) {
        CommentResponse response = commentService.addComment(projectId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("Comment created (id={}) for projectId={}", response.getId(), projectId);

        return ResponseEntity.created(location).body(response);
    }


    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long projectId) {
        List<CommentResponse> comments = commentService.getProjectComments(projectId);
        return ResponseEntity.ok(comments);
    }


    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{commentId}/reply")
    public ResponseEntity<CommentResponse> reply(
            @PathVariable Long projectId,
            @PathVariable Long commentId,
            @Valid @RequestBody AddCommentRequest request
    ) {
        CommentResponse response = commentService.replyToComment(commentId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("Reply created (id={}) to commentId={} in projectId={}", response.getId(), commentId, projectId);

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<CommentResponse>> getReplies(
            @PathVariable Long projectId,
            @PathVariable Long commentId
    ) {
        logger.debug("Fetching replies for commentId={} in projectId={}", commentId, projectId);
        List<CommentResponse> replies = commentService.getReplies(commentId);
        return ResponseEntity.ok(replies);
    }
}
