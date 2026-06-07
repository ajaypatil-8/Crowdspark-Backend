// src/main/java/Crowdspark/Crowdspark/service/impl/AnalyticsServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.ProjectAnalyticsResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectView;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.*;
import Crowdspark.Crowdspark.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ProjectViewRepository     viewRepository;
    private final ProjectRepository         projectRepository;
    private final DonationRepository        donationRepository;
    private final CampaignUpdateRepository  updateRepository;
    private final ProjectCommentRepository  commentRepository;
    private final SavedProjectRepository    savedRepository;
    private final StringRedisTemplate       redis;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── recordView ────────────────────────────────────────────────────────────

    @Override
    @Async
    @Transactional
    public void recordView(Long projectId, String visitorKey) {
        try {
            // 1. Increment daily DB counter (upsert)
            viewRepository.incrementViewCount(projectId);

            // 2. Track unique visitor using Redis HyperLogLog (memory-efficient)
            //    PFADD returns 1 if element added (new unique), 0 if already seen
            String hlKey = hllKey(projectId, LocalDate.now());
            redis.opsForHyperLogLog().add(hlKey, visitorKey);

            // Expire HLL key after 48h — we flush to DB nightly
            redis.expire(hlKey, 48, java.util.concurrent.TimeUnit.HOURS);

        } catch (Exception e) {
            // Non-critical — analytics failure should not affect the user
            log.warn("Failed to record view for project {}: {}", projectId, e.getMessage());
        }
    }

    // ── getAnalytics ──────────────────────────────────────────────────────────

    @Override
    public ProjectAnalyticsResponse getAnalytics(Long projectId, Long requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getCreator().getId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the project creator can view analytics");
        }

        // ── Views ─────────────────────────────────────────────────────────────
        long totalViews   = viewRepository.sumViewsByProject(projectId);
        long totalUnique  = viewRepository.sumUniqueByProject(projectId);

        LocalDate today   = LocalDate.now();
        LocalDate day7    = today.minusDays(6);
        LocalDate day30   = today.minusDays(29);

        List<ProjectView> last30 = viewRepository
                .findByProject_IdAndViewDateBetweenOrderByViewDateAsc(projectId, day30, today);

        long views7d  = last30.stream()
                .filter(v -> !v.getViewDate().isBefore(day7))
                .mapToLong(ProjectView::getViewCount).sum();
        long views30d = last30.stream()
                .mapToLong(ProjectView::getViewCount).sum();

        // Build daily views map for quick lookup
        Map<LocalDate, Long> viewsByDate = last30.stream()
                .collect(Collectors.toMap(ProjectView::getViewDate, ProjectView::getViewCount));

        // ── Donations ─────────────────────────────────────────────────────────
        List<Donation> successDonations = donationRepository
                .findByProject_IdAndPaymentStatus(projectId, PaymentStatus.SUCCESS);

        long   backersCount  = successDonations.size();
        double totalRaised   = successDonations.stream().mapToDouble(Donation::getAmount).sum();
        double avgDonation   = backersCount > 0 ? totalRaised / backersCount : 0.0;
        double conversion    = totalViews > 0
                ? Math.round((backersCount * 100.0 / totalViews) * 100.0) / 100.0 : 0.0;

        // ── Engagement ────────────────────────────────────────────────────────
        long updatesCount  = updateRepository.countByProject_Id(projectId);
        long commentsCount = commentRepository.countActiveByProjectId(projectId);
        long savedCount    = savedRepository.countByProject_Id(projectId);

        // ── Funding %  ────────────────────────────────────────────────────────
        int fundedPct = project.getGoalAmount() > 0
                ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100) : 0;

        // ── Build 30-day time series ──────────────────────────────────────────
        List<ProjectAnalyticsResponse.DailyDataPoint> dailyViews    = new ArrayList<>();
        List<ProjectAnalyticsResponse.DailyDataPoint> dailyFunding  = new ArrayList<>();

        // Funding: group SUCCESS donations by date for cumulative chart
        Map<LocalDate, Double> fundingByDate = successDonations.stream()
                .filter(d -> d.getPaidAt() != null &&
                             !d.getPaidAt().toLocalDate().isBefore(day30))
                .collect(Collectors.groupingBy(
                        d -> d.getPaidAt().toLocalDate(),
                        Collectors.summingDouble(Donation::getAmount)));

        double cumulativeFunding = successDonations.stream()
                .filter(d -> d.getPaidAt() != null &&
                             d.getPaidAt().toLocalDate().isBefore(day30))
                .mapToDouble(Donation::getAmount).sum();

        for (LocalDate date = day30; !date.isAfter(today); date = date.plusDays(1)) {
            String label = date.format(DATE_FMT);

            dailyViews.add(ProjectAnalyticsResponse.DailyDataPoint.builder()
                    .date(label)
                    .value(viewsByDate.getOrDefault(date, 0L))
                    .build());

            cumulativeFunding += fundingByDate.getOrDefault(date, 0.0);
            dailyFunding.add(ProjectAnalyticsResponse.DailyDataPoint.builder()
                    .date(label)
                    .value((long) cumulativeFunding)
                    .build());
        }

        return ProjectAnalyticsResponse.builder()
                .projectId(projectId)
                .projectTitle(project.getTitle())
                .status(project.getStatus().name())
                .goalAmount(project.getGoalAmount())
                .currentAmount(project.getCurrentAmount())
                .remainingAmount(Math.max(0, project.getGoalAmount() - project.getCurrentAmount()))
                .fundedPercentage(fundedPct)
                .backersCount(backersCount)
                .avgDonationAmount(Math.round(avgDonation * 100.0) / 100.0)
                .conversionRate(conversion)
                .totalViews(totalViews)
                .totalUniqueVisitors(totalUnique)
                .viewsLast7Days(views7d)
                .viewsLast30Days(views30d)
                .updatesCount(updatesCount)
                .commentsCount(commentsCount)
                .savedCount(savedCount)
                .dailyViews(dailyViews)
                .dailyFunding(dailyFunding)
                .build();
    }

    // ── Nightly flush: Redis HLL → PostgreSQL unique counts ──────────────────

    @Override
    @Scheduled(cron = "0 0 0 * * *")   // midnight every day
    @Transactional
    public void flushUniquesToDb() {
        log.info("Analytics: flushing HyperLogLog unique counts to DB");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Get all project IDs that have an HLL key for yesterday
        String pattern = "hll:views:*:" + yesterday.format(DATE_FMT);
        var keys = redis.keys(pattern);
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                Long projectId = Long.parseLong(key.split(":")[2]);
                Long uniqueCount = redis.opsForHyperLogLog().size(key);
                if (uniqueCount != null && uniqueCount > 0) {
                    viewRepository.updateUniqueCount(projectId, uniqueCount);
                }
                redis.delete(key);
            } catch (Exception e) {
                log.warn("Failed to flush HLL key {}: {}", key, e.getMessage());
            }
        }
        log.info("Analytics: flush complete for {}", yesterday);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String hllKey(Long projectId, LocalDate date) {
        return "hll:views:" + projectId + ":" + date.format(DATE_FMT);
    }
}
