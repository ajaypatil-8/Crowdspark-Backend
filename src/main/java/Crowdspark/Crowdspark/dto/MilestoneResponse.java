// src/main/java/Crowdspark/Crowdspark/dto/MilestoneResponse.java

package Crowdspark.Crowdspark.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneResponse {

    private Long   id;
    private Long   projectId;
    private String title;
    private String description;

    /** Optional unlock amount — null if not set */
    private Double  targetAmount;

    private Integer sortOrder;

    /**
     * PENDING or COMPLETED — derived from completedAt.
     */
    private String status;

    /** Non-null when status is COMPLETED */
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
