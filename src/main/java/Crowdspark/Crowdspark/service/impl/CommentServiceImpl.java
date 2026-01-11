package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.AddCommentRequest;
import Crowdspark.Crowdspark.dto.CommentResponse;
import Crowdspark.Crowdspark.entity.Comment;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.CommentRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.CommentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    /* ================= ADD COMMENT ================= */

    @Override
    public CommentResponse addComment(Long projectId, AddCommentRequest request) {

        User user = getCurrentUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new AuthException("Cannot comment on unapproved project");
        }

        Comment comment = new Comment();
        comment.setProjectId(projectId);
        comment.setUserId(user.getId());
        comment.setContent(request.getContent());

        Comment saved = commentRepository.save(comment);

        auditLogService.log(
                user.getId(),
                "COMMENT_ADDED",
                "PROJECT",
                projectId
        );

        return modelMapper.map(saved, CommentResponse.class);
    }

    /* ================= REPLY TO COMMENT ================= */

    @Override
    public CommentResponse replyToComment(Long commentId, AddCommentRequest request) {

        User user = getCurrentUser();

        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new AuthException("Comment not found"));

        Project project = projectRepository.findById(parent.getProjectId())
                .orElseThrow(() -> new AuthException("Project not found"));

        boolean isAdmin = user.getRoles().contains(Role.ADMIN);
        boolean isCreatorOwner =
                user.getRoles().contains(Role.CREATOR)
                        && project.getCreatorId().equals(user.getId());

        if (!isAdmin && !isCreatorOwner) {
            throw new AuthException("Not allowed to reply");
        }




        Comment reply = new Comment();
        reply.setProjectId(parent.getProjectId());
        reply.setUserId(user.getId());
        reply.setContent(request.getContent());
        reply.setParentCommentId(parent.getId());

        Comment saved = commentRepository.save(reply);

        auditLogService.log(
                user.getId(),
                "COMMENT_REPLIED",
                "PROJECT",
                parent.getProjectId()
        );

        return modelMapper.map(saved, CommentResponse.class);
    }

    /* ================= VIEW COMMENTS ================= */

    @Override
    public List<CommentResponse> getProjectComments(Long projectId) {
        return commentRepository
                .findByProjectIdAndParentCommentIdIsNull(projectId)
                .stream()
                .map(this::mapComment)
                .toList();
    }

    @Override
    public List<CommentResponse> getReplies(Long parentCommentId) {
        return commentRepository
                .findByParentCommentId(parentCommentId)
                .stream()
                .map(this::mapComment)
                .toList();
    }

    /* ================= HELPERS ================= */

    private User getCurrentUser() {
        String username =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    private CommentResponse mapComment(Comment comment) {
        List<CommentResponse> replies =
                commentRepository.findByParentCommentId(comment.getId())
                        .stream()
                        .map(this::mapComment)
                        .toList();

        return new CommentResponse(
                comment.getId(),
                comment.getUserId(),
                comment.getContent(),
                comment.getCreatedAt(),
                replies
        );
    }

}

