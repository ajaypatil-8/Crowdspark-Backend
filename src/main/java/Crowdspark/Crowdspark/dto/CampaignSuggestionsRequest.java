// src/main/java/Crowdspark/Crowdspark/dto/CampaignSuggestionsRequest.java
// Feature #46 — AI Campaign Improvement Suggestions

package Crowdspark.Crowdspark.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CampaignSuggestionsRequest {

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

    private boolean hasVideo;

    private boolean hasThumbnail;

    private int mediaCount;

    // Actual tier data, not just a count — this is the one meaningful
    // difference from CampaignScoreRequest (#41), and the reason this
    // feature needed its own request shape rather than reusing that one:
    // gap-in-the-price-ladder suggestions need the real amounts to reason
    // about, not just "there are 2 tiers".
    @Size(max = 20, message = "Too many reward tiers")
    @Valid
    private List<RewardTierSummary> rewardTiers;

    @Data
    public static class RewardTierSummary {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotNull
        @DecimalMin(value = "0")
        private Double minimumAmount;

        @Size(max = 300)
        private String description; // optional — truncated client-side if long
    }
}
