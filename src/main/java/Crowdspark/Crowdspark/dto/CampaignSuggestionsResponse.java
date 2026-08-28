// src/main/java/Crowdspark/Crowdspark/dto/CampaignSuggestionsResponse.java
// Feature #46 — AI Campaign Improvement Suggestions

package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSuggestionsResponse {

    /** 0-3 alternative title ideas — empty is correct if the current title is already strong. */
    private List<String> titleSuggestions;

    /** 0-4 specific reward-tier suggestions (gaps, missing entry tier, unclear description, etc.). */
    private List<String> rewardSuggestions;

    /** 0-3 specific media gaps (missing video, product shots, creator photo, etc.). */
    private List<String> mediaSuggestions;

    /** Short wrap-up note, or empty string if the per-category lists say it all. */
    private String overallNote;

    private String model;
}
