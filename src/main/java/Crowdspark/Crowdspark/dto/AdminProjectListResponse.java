package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminProjectListResponse {

    private Long id;
    private String title;

    private String creatorUsername;
    private String creatorEmail;

    private String thumbnailUrl;

    private Double goalAmount;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;

    private String status;

    // Feature #43 — null when never scanned (e.g. project predates this
    // feature); "PENDING" while the async scan hasn't completed yet.
    private String  fraudCheckStatus;
    private Integer fraudRiskScore;
    private String  fraudRiskLevel;
    private String  fraudReasoning;
}
