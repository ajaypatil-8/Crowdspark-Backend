// src/main/java/Crowdspark/Crowdspark/dto/CreatorWeeklyInsightResponse.java
// Feature #48 — AI Creator Analytics Insights (dashboard-facing)

package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CreatorWeeklyInsightResponse {
    private Long projectId;
    private String projectTitle;
    private String summary;
    private int fundedPercent;
    private int newBackersThisWeek;
    private int viewsThisWeek;
    private LocalDate weekStart;
}
