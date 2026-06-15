package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RewardClaimStatusRequest {

    /** New status: PROCESSING | SHIPPED | FULFILLED | CANCELLED */
    @NotBlank
    @Pattern(
            regexp = "PROCESSING|SHIPPED|FULFILLED|CANCELLED",
            message = "Status must be one of: PROCESSING, SHIPPED, FULFILLED, CANCELLED"
    )
    private String status;

    /** Optional tracking number — required when status = SHIPPED */
    @Size(max = 255)
    private String trackingNumber;

    /** Optional message to the backer */
    @Size(max = 1000)
    private String fulfillmentNote;
}