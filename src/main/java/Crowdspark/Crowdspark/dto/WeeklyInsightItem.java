// src/main/java/Crowdspark/Crowdspark/dto/WeeklyInsightItem.java
// Feature #48 — AI Creator Analytics Insights
// One entry in a creator's weekly digest email — deliberately a standalone
// type here rather than nested inside EmailServiceImpl (where the queue
// payload records like WelcomeEmailPayload live): those are impl-internal
// and only ever touched by EmailServiceImpl/EmailJobWorker, but this one
// appears in EmailService's public interface signature, and is also built
// by CreatorInsightSchedulerService — a type in the public interface
// shouldn't reach into another class's implementation package.

package Crowdspark.Crowdspark.dto;

public record WeeklyInsightItem(
        String projectTitle,
        Long projectId,
        String summary,
        int fundedPercent,
        int newBackersThisWeek
) {}
