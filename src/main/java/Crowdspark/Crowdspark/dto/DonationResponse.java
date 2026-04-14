package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DonationResponse {

    private Long id;
    private Long projectId;
    private String projectTitle;
    private String projectThumbnailUrl;

    private Long backerId;
    private String backerUsername;

    private Double amount;
    private String paymentStatus;
    private String transactionId;
    private String message;

    private Long rewardTierId;
    private String rewardTierTitle;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
