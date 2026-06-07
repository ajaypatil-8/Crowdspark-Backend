// src/main/java/Crowdspark/Crowdspark/dto/ProjectAnalyticsResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectAnalyticsResponse {

    private Long   projectId;
    private String projectTitle;
    private String status;

    // ── Funding summary ───────────────────────────────────────────────────────
    private Double goalAmount;
    private Double currentAmount;
    private Double remainingAmount;
    private int    fundedPercentage;
    private long   backersCount;
    private Double avgDonationAmount;
    private Double conversionRate;     // backers / totalViews × 100 (%)

    // ── Views summary ─────────────────────────────────────────────────────────
    private long totalViews;
    private long totalUniqueVisitors;
    private long viewsLast7Days;
    private long viewsLast30Days;

    // ── Engagement ────────────────────────────────────────────────────────────
    private long updatesCount;
    private long commentsCount;
    private long savedCount;           // how many users saved this project

    // ── Time series (last 30 days) ────────────────────────────────────────────
    private List<DailyDataPoint> dailyViews;     // views per day chart
    private List<DailyDataPoint> dailyFunding;   // cumulative funding per day chart

    @Data
    @Builder
    public static class DailyDataPoint {
        private String date;    // "2025-01-15"
        private long   value;
    }
}
