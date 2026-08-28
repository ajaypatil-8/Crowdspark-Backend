// src/main/java/Crowdspark/Crowdspark/service/impl/ProjectCommentServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.ProjectCommentRequest;
import Crowdspark.Crowdspark.dto.ProjectCommentResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectComment;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.ProjectCommentRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AiService;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.ProjectCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCommentServiceImpl implements ProjectCommentService {

    private final ProjectCommentRepository commentRepository;
    private final ProjectRepository        projectRepository;
    private final UserRepository           userRepository;
    private final NotificationService      notificationService;
    private final AiService                aiService; // Feature #45 — queues async moderation scan on post

    @Override
    public Page<ProjectCommentResponse> getComments(Long projectId, int page, int size) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        Long creatorId = projectRepository.findById(projectId)
                .map(p -> p.getCreator().getId()).orElse(null);

        return commentRepository
                .findTopLevelByProjectId(projectId, PageRequest.of(page, size))
                .map(c -> toResponse(c, creatorId, true));
    }

    @Override
    @Transactional
    public ProjectCommentResponse postComment(Long projectId,
                                              ProjectCommentRequest request,
                                              Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        ProjectComment comment = new ProjectComment();
        comment.setProject(project);
        comment.setAuthor(author);
        comment.setContent(request.getContent().trim());

        // Handle reply
        if (request.getParentCommentId() != null) {
            ProjectComment parent = commentRepository
                    .findById(request.getParentCommentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Parent comment not found"));

            // Only allow one level of nesting
            if (parent.getParentComment() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Replies to replies are not supported");
            }
            if (!parent.getProject().getId().equals(projectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Comment does not belong to this project");
            }
            comment.setParentComment(parent);
        }

        ProjectComment saved = commentRepository.save(comment);
        aiService.queueCommentModerationScan(saved.getId()); // Feature #45 — async, never blocks this request
        Long creatorId = project.getCreator().getId();

        // Notifications
        boolean isTopLevel = request.getParentCommentId() == null;
        boolean commenterIsCreator = userId.equals(creatorId);

        if (isTopLevel && !commenterIsCreator) {
            // New top-level comment → notify creator
            notificationService.notifyCreatorNewComment(project, author);
        } else if (!isTopLevel) {
            // Reply → notify the parent comment's author (if not same person)
            ProjectComment parent = saved.getParentComment();
            User parentAuthor = parent.getAuthor();
            if (!parentAuthor.getId().equals(userId)) {
                notificationService.notifyUserCommentReplied(
                        parentAuthor, project, author.getUsername());
            }
            // Also notify creator if creator didn't make the reply
            if (!commenterIsCreator) {
                notificationService.notifyCreatorNewComment(project, author);
            }
        }

        log.info("Comment posted: id={} project={} author={} isReply={}",
                saved.getId(), projectId, userId, !isTopLevel);

        return toResponse(saved, creatorId, false);
    }

    @Override
    @Transactional
    public void deleteComment(Long projectId, Long commentId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        ProjectComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found"));

        if (!comment.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comment does not belong to this project");
        }

        boolean isAuthor    = comment.getAuthor().getId().equals(userId);
        boolean isCreator   = project.getCreator().getId().equals(userId);

        if (!isAuthor && !isCreator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the comment author or project creator can delete this comment");
        }

        // Soft delete — preserve thread structure
        comment.setDeleted(true);
        comment.setContent("[deleted]");
        commentRepository.save(comment);
        log.info("Comment soft-deleted: id={} by userId={}", commentId, userId);
    }

    // ── mapper ────────────────────────────────────────────────────────────────

    private ProjectCommentResponse toResponse(ProjectComment c,
                                               Long creatorId,
                                               boolean includeReplies) {
        List<ProjectCommentResponse> replies = List.of();
        if (includeReplies && c.getReplies() != null) {
            replies = c.getReplies().stream()
                    .map(r -> toResponse(r, creatorId, false))
                    .toList();
        }

        return ProjectCommentResponse.builder()
                .id(c.getId())
                .projectId(c.getProject().getId())
                .authorId(c.getAuthor().getId())
                .authorUsername(c.getAuthor().getUsername())
                .authorProfileImage(c.getAuthor().getProfileImageUrl())
                .authorIsCreator(c.getAuthor().getId().equals(creatorId))
                .parentCommentId(c.getParentComment() != null
                        ? c.getParentComment().getId() : null)
                .content(c.isDeleted() ? "[deleted]" : c.getContent())
                .deleted(c.isDeleted())
                .replies(replies)
                .replyCount(c.getReplies() != null ? c.getReplies().size() : 0)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
