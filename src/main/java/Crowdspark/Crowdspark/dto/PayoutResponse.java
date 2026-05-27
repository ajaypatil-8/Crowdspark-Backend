// src/main/java/Crowdspark/Crowdspark/dto/PayoutResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PayoutResponse {
    private Long   id;
    private Long   projectId;
    private String projectTitle;
    private Long   creatorId;
    private String creatorUsername;
    private String creatorUpiId;

    private Double grossAmount;
    private Double platformFeePercent;
    private Double platformFeeAmount;
    private Double netAmount;

    private String status;           // INITIATED / PROCESSING / COMPLETED / FAILED
    private String payoutMode;       // UPI / BANK_TRANSFER
    private String razorpayPayoutId;
    private String failureReason;

    private String initiatedByUsername;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
}
