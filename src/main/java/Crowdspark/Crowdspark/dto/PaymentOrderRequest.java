// src/main/java/Crowdspark/Crowdspark/dto/PaymentOrderRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • amount:  added @Max(value=500_000) — ₹5 lakh per single donation cap;
//              was only @Min(1) so someone could request a ₹999 crore Razorpay order
//   • message: added @Size(max=500) — no constraint existed; could be an arbitrarily
//              long string stored in the donations table

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PaymentOrderRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Minimum donation is ₹1")
    @Max(value = 500_000,
         message = "Maximum single donation is ₹5,00,000 (5 lakh)")
    private Double amount;

    /** Optional reward tier the backer wants to claim */
    private Long rewardTierId;

    /** Optional personal message to the creator */
    @Size(max = 500, message = "Message to creator must be 500 characters or less")
    private String message;
}
