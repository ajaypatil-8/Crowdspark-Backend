// src/main/java/Crowdspark/Crowdspark/dto/CampaignScoreRequest.java
// Feature #41 — AI Campaign Success Predictor

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampaignScoreRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @NotBlank(message = "Short description is required")
    @Size(max = 300, message = "Short description must be under 300 characters")
    private String shortDescription;

    @NotBlank(message = "Full description is required")
    @Size(max = 20_000, message = "Full description must be under 20,000 characters")
    private String fullDescription;

    @Size(max = 100, message = "Category must be under 100 characters")
    private String category;

    @NotNull(message = "Goal amount is required")
    @DecimalMin(value = "1000", message = "Goal amount must be at least ₹1,000")
    @DecimalMax(value = "100000000", message = "Goal amount must be under ₹10,00,00,000")
    private Double goalAmount;

    @NotNull(message = "Campaign length in days is required")
    @Min(value = 1, message = "Campaign must run at least 1 day")
    @Max(value = 365, message = "Campaign must run under 365 days")
    private Integer durationDays;

    @Min(value = 0, message = "Media count cannot be negative")
    private int mediaCount;

    private boolean hasVideo;

    private boolean hasThumbnail;

    @Min(value = 0, message = "Reward tier count cannot be negative")
    private int rewardTierCount;
}
