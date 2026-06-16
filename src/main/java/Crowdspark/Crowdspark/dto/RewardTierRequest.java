// src/main/java/Crowdspark/Crowdspark/dto/RewardTierRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • title:          added @Size(max=150) — was only @NotBlank with no length cap
//   • description:    added @Size(max=1000)
//   • minimumAmount:  added @Max(value=10_000_000) — ₹1 crore cap per reward tier;
//                     was only @Positive, so someone could set ₹999 crore
//   • estimatedDelivery: added @Size(max=100) — no constraints existed

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RewardTierRequest {

    @NotBlank(message = "Reward title is required")
    @Size(min = 3, max = 150,
          message = "Reward title must be between 3 and 150 characters")
    private String title;

    @Size(max = 1000, message = "Description must be 1000 characters or less")
    private String description;

    @NotNull(message = "Minimum amount is required")
    @Positive(message = "Minimum amount must be greater than zero")
    @Max(value = 10_000_000,
         message = "Minimum amount cannot exceed ₹1,00,00,000 (1 crore)")
    private Double minimumAmount;

    /** Optional: expected delivery date or month/year string (e.g. "March 2026") */
    @Size(max = 100, message = "Estimated delivery must be 100 characters or less")
    private String estimatedDelivery;

    /** Optional: cap on the number of backers that can claim this tier */
    @Min(value = 1, message = "Limited quantity must be at least 1")
    @Max(value = 100_000, message = "Limited quantity cannot exceed 1,00,000")
    private Integer limitedQuantity;
}
