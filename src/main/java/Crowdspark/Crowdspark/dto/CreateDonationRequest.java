package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDonationRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Minimum donation is ₹1")
    private Double amount;

    /** Optional reward tier — null means no reward selected */
    private Long rewardTierId;

    /** Optional message to creator */
    private String message;

    /** External transaction id from payment gateway */
    private String transactionId;
}
