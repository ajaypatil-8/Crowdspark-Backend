// src/main/java/Crowdspark/Crowdspark/dto/ProjectReviewResponse.java

package Crowdspark.Crowdspark.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectReviewResponse {

    private Long   id;
    private Long   projectId;
    private Long   reviewerId;
    private String reviewerName;
    private String reviewerUsername;
    private String reviewerProfileImageUrl;

    /** Star rating 1–5 */
    private Integer rating;

    private String title;
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** True when the authenticated user authored this review */
    private boolean myReview;
}
