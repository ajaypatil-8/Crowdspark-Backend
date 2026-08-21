// src/main/java/Crowdspark/Crowdspark/dto/GenerateDescriptionResponse.java
// Feature #39 — AI Campaign Description Generator (response)

package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDescriptionResponse {

    /** Punchy 1-2 sentence hook, <=300 chars — maps onto Project.shortDescription. */
    private String shortPitch;

    /** Full Markdown campaign story — maps onto Project.fullDescription. */
    private String fullDescription;

    /** Rough goal estimate in whole INR, clamped to the platform's allowed range. */
    private Double suggestedGoalAmount;

    /** 1-2 sentence plain-text explanation of the suggested goal. */
    private String goalReasoning;

    /** Which model produced this draft — shown in the UI for transparency. */
    private String model;
}
