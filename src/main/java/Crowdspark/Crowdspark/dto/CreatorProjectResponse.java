package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CreatorProjectResponse {

    private Long id;
    private String title;
    private String thumbnailUrl;

    private Double goalAmount;
    private Double currentAmount;

    private String status;
    private String rejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime deadline;
}
