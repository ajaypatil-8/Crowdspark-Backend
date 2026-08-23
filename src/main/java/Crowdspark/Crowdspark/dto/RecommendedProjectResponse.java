// src/main/java/Crowdspark/Crowdspark/dto/RecommendedProjectResponse.java
// Feature #40 — AI-Powered Project Recommendations

package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedProjectResponse {

    /** Reuses the same card shape the followed-creator feed already returns. */
    private ProjectFeedResponse project;

    /** One-sentence, model-written reason this project was picked for this backer. */
    private String reason;
}
