// src/main/java/Crowdspark/Crowdspark/dto/AdminFlaggedCommentResponse.java
// Feature #45 — AI Content Moderation
// Scoped to comments only — flagged projects already surface in the
// existing admin/projects queue (AdminProjectListResponse), so showing them
// again here would just be duplication of an already-covered case.

package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminFlaggedCommentResponse {

    private Long checkId;      // ContentModerationCheck.id — pass back to resolve()

    private Long commentId;
    private String commentContent;
    private String commentAuthorUsername;
    private boolean deleted;   // should be true (auto-hidden); shown for admin confidence, not for logic

    private Long projectId;
    private String projectTitle;

    private String category;
    private String reasoning;
    private LocalDateTime flaggedAt;
}
