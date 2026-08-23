// src/main/java/Crowdspark/Crowdspark/dto/RecommendationsResponse.java
// Feature #40 — AI-Powered Project Recommendations

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
public class RecommendationsResponse {

    private List<RecommendedProjectResponse> recommendations;

    /**
     * true  = based on real signal (backed/interested categories, or recent views)
     * false = cold start -- no signal yet, these are trending/recent picks instead
     * so the frontend can head the section honestly ("Recommended for you" vs
     * "Trending on CrowdSpark") instead of pretending everything is personalized.
     */
    private boolean personalized;
}
