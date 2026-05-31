// src/main/java/Crowdspark/Crowdspark/service/ProjectCommentService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ProjectCommentRequest;
import Crowdspark.Crowdspark.dto.ProjectCommentResponse;
import org.springframework.data.domain.Page;

public interface ProjectCommentService {

    /** Public — paginated top-level comments with replies */
    Page<ProjectCommentResponse> getComments(Long projectId, int page, int size);

    /** Authenticated — post a comment or reply */
    ProjectCommentResponse postComment(Long projectId, ProjectCommentRequest request, Long userId);

    /** Author or project creator can delete a comment (soft delete) */
    void deleteComment(Long projectId, Long commentId, Long userId);
}
