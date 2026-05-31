// src/main/java/Crowdspark/Crowdspark/dto/ProjectCommentResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectCommentResponse {
    private Long   id;
    private Long   projectId;
    private Long   authorId;
    private String authorUsername;
    private String authorProfileImage;
    private boolean authorIsCreator;    // true if commenter is the project creator
    private Long   parentCommentId;     // null for top-level comments
    private String content;             // "[deleted]" when soft-deleted
    private boolean deleted;
    private List<ProjectCommentResponse> replies;   // populated for top-level only
    private int    replyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
