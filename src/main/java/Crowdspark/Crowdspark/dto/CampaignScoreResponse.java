// src/main/java/Crowdspark/Crowdspark/dto/CampaignScoreResponse.java
// Feature #41 — AI Campaign Success Predictor

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
public class CampaignScoreResponse {

    /** 0-100, clamped server-side regardless of what the model returns. */
    private int score;

    /** Short label, e.g. "Strong", "Promising", "Needs Work", "High Risk". */
    private String verdict;

    /** 2-3 sentence honest assessment — instructed not to just praise. */
    private String explanation;

    /** 3-5 specific, actionable suggestions, most impactful first. */
    private List<String> tips;

    private String model;
}
