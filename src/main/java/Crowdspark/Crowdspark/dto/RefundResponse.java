// src/main/java/Crowdspark/Crowdspark/dto/RefundResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RefundResponse {
    private Long   id;
    private Long   donationId;
    private Long   projectId;
    private String projectTitle;
    private Long   backerId;
    private String backerUsername;
    private Double amount;
    private String status;             // INITIATED / COMPLETED / FAILED
    private String razorpayRefundId;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
}
