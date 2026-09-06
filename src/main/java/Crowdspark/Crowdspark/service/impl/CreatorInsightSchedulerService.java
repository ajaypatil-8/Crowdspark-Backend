// src/main/java/Crowdspark/Crowdspark/service/impl/CreatorInsightSchedulerService.java
// Feature #48 — AI Creator Analytics Insights
//
// Same structural pattern as DeadlineSchedulerService (Feature #2): one
// @Scheduled entry point, a per-item try/catch loop so one failure can't
// take down the rest of the week's batch, and everything logged clearly.
// Runs weekly rather than hourly, and additionally groups results by
// creator to send one digest email instead of one per campaign.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.WeeklyInsightItem;
import Crowdspark.Crowdspark.entity.CampaignWeeklyInsight;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.CampaignWeeklyInsightRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.ProjectViewRepository;
import Crowdspark.Crowdspark.service.AiService;
import Crowdspark.Crowdspark.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorInsightSchedulerService {

    private final ProjectRepository               projectRepository;
    private final DonationRepository               donationRepository;
    private final ProjectViewRepository            projectViewRepository;
    private final CampaignWeeklyInsightRepository  insightRepository;
    private final AiService                        aiService;
    private final EmailService                     emailService;

    /** Every Monday, 8am server time — same "no explicit zone" convention
     *  DeadlineSchedulerService already uses elsewhere in this codebase. */
    @Scheduled(cron = "0 0 8 * * MON")
    @Transactional
    public void generateWeeklyInsights() {

        List<Project> liveProjects = projectRepository.findByStatus(ProjectStatus.APPROVED);
        if (liveProjects.isEmpty()) {
            log.debug("Weekly insight scheduler: no live campaigns — nothing to do.");
            return;
        }
        log.info("Weekly insight scheduler: processing {} live campaign(s).", liveProjects.size());

        LocalDate     weekStart   = LocalDate.now();
        LocalDate     since1Week  = weekStart.minusDays(7);
        LocalDate     since2Weeks = weekStart.minusDays(14);
        LocalDateTime since1WeekDt  = since1Week.atStartOfDay();
        LocalDateTime since2WeeksDt = since2Weeks.atStartOfDay();

        // Grouped by creator so each person gets ONE digest email rather
        // than one per live campaign.
        Map<User, List<WeeklyInsightItem>> byCreator = new LinkedHashMap<>();

        for (Project project : liveProjects) {
            try {
                WeeklyInsightItem item = generateSingle(project, weekStart, since1Week, since2Weeks,
                        since1WeekDt, since2WeeksDt);
                if (item != null) {
                    byCreator.computeIfAbsent(project.getCreator(), c -> new ArrayList<>()).add(item);
                }
            } catch (Exception e) {
                log.error("Weekly insight scheduler: error on project id={}: {}", project.getId(), e.getMessage(), e);
            }
        }

        byCreator.forEach((creator, items) -> {
            try {
                emailService.sendWeeklyInsightDigestEmail(creator.getEmail(), creator.getName(), items);
            } catch (Exception e) {
                log.error("Weekly insight scheduler: digest email failed for creator id={}: {}",
                        creator.getId(), e.getMessage(), e);
            }
        });

        log.info("Weekly insight scheduler: done — {} creator(s) emailed.", byCreator.size());
    }

    /** Returns null (and skips this project entirely) if the AI text
     *  couldn't be generated — e.g. GROQ_API_KEY unset, or Groq errored.
     *  Nothing partial gets stored or emailed for that project this week. */
    private WeeklyInsightItem generateSingle(Project project, LocalDate weekStart,
                                              LocalDate since1Week, LocalDate since2Weeks,
                                              LocalDateTime since1WeekDt, LocalDateTime since2WeeksDt) {

        long viewsThisWeek = projectViewRepository.sumViewsByProjectAndDateBetween(
                project.getId(), since1Week, weekStart);
        long newBackersThisWeek = donationRepository.countByProject_IdAndPaymentStatusAndCreatedAtBetween(
                project.getId(), PaymentStatus.SUCCESS, since1WeekDt, LocalDateTime.now());
        long newBackersLastWeek = donationRepository.countByProject_IdAndPaymentStatusAndCreatedAtBetween(
                project.getId(), PaymentStatus.SUCCESS, since2WeeksDt, since1WeekDt);
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), project.getDeadline()));

        String summary = aiService.generateWeeklyInsightText(
                project.getTitle(), project.getCurrentAmount(), project.getGoalAmount(),
                daysLeft, viewsThisWeek, newBackersThisWeek, newBackersLastWeek);

        if (summary == null) {
            log.warn("Weekly insight scheduler: no summary generated for project id={} — skipping", project.getId());
            return null;
        }

        int fundedPercent = project.getGoalAmount() != null && project.getGoalAmount() > 0
                ? (int) Math.round((project.getCurrentAmount() / project.getGoalAmount()) * 100) : 0;

        CampaignWeeklyInsight insight = new CampaignWeeklyInsight();
        insight.setProject(project);
        insight.setWeekStart(weekStart);
        insight.setSummaryText(summary);
        insight.setViewsThisWeek((int) viewsThisWeek);
        insight.setNewBackersThisWeek((int) newBackersThisWeek);
        insight.setFundedPercent(fundedPercent);
        insight.setModel(aiService.getConfiguredModel());
        insightRepository.save(insight);

        return new WeeklyInsightItem(project.getTitle(), project.getId(), summary,
                fundedPercent, (int) newBackersThisWeek);
    }
}
