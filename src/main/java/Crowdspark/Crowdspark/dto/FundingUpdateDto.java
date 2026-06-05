// src/main/java/Crowdspark/Crowdspark/dto/FundingUpdateDto.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Payload pushed via SSE whenever a new donation is confirmed.
 * The browser uses this to update the funding bar without a page reload.
 */
@Data
@Builder
public class FundingUpdateDto {
    private Long   projectId;
    private Double currentAmount;
    private Double goalAmount;
    private int    fundedPercentage;
    private long   backersCount;
    private String status;          // APPROVED / FUNDED / FAILED / CLOSED
    private long   timestamp;       // epoch millis — lets browser detect stale events
}
