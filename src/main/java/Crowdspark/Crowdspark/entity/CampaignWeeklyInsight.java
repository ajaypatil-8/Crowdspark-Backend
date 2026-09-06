// src/main/java/Crowdspark/Crowdspark/entity/CampaignWeeklyInsight.java
// Feature #48 — AI Creator Analytics Insights
// One row per (project, week) — new row each week, no upsert, so the
// dashboard can show the latest and (later, if wanted) a history.

package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "campaign_weekly_insights",
        indexes = {
                @Index(name = "idx_weekly_insights_project_id", columnList = "project_id, week_start")
        })
public class CampaignWeeklyInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summaryText;

    private int viewsThisWeek;
    private int newBackersThisWeek;
    private int fundedPercent;

    private String model;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;
}
