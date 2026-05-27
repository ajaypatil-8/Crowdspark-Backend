// src/main/java/Crowdspark/Crowdspark/service/impl/DeadlineSchedulerService.java
// NEW FILE — @Scheduled job that runs every hour and transitions
// expired APPROVED campaigns to FUNDED or FAILED.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineSchedulerService {

    private final ProjectRepository    projectRepository;
    private final DonationRepository   donationRepository;
    private final NotificationService  notificationService;

    /**
     * Runs every hour at :00.
     * Finds all APPROVED campaigns whose deadline has passed and
     * transitions them to FUNDED or FAILED based on whether the
     * goal was reached.
     *
     * Cron format: second minute hour day month weekday
     * "0 0 * * * *" = top of every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void processExpiredCampaigns() {
        List<Project> expired = projectRepository.findExpiredApprovedProjects(LocalDateTime.now());

        if (expired.isEmpty()) {
            log.debug("Deadline scheduler: no expired campaigns found.");
            return;
        }

        log.info("Deadline scheduler: processing {} expired campaign(s).", expired.size());

        for (Project project : expired) {
            try {
                processSingle(project);
            } catch (Exception e) {
                // Log and continue — one failed project should not block the rest
                log.error("Deadline scheduler: error processing project id={} title=\"{}\": {}",
                        project.getId(), project.getTitle(), e.getMessage(), e);
            }
        }

        log.info("Deadline scheduler: done.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single campaign processing
    // ─────────────────────────────────────────────────────────────────────────

    private void processSingle(Project project) {
        boolean goalReached = project.getCurrentAmount() >= project.getGoalAmount();

        if (goalReached) {
            // ── FUNDED: goal was reached ──────────────────────────────────────
            project.setStatus(ProjectStatus.FUNDED);
            projectRepository.save(project);
            log.info("Project id={} \"{}\" → FUNDED (raised {} of {})",
                    project.getId(), project.getTitle(),
                    project.getCurrentAmount(), project.getGoalAmount());

            // Notify creator
            notificationService.notifyCreatorCampaignFunded(project);

            // Notify every backer (async — non-blocking)
            notifyAllBackers(project, true);

        } else {
            // ── FAILED: deadline passed, goal not reached ─────────────────────
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
            log.info("Project id={} \"{}\" → FAILED (raised {} of {})",
                    project.getId(), project.getTitle(),
                    project.getCurrentAmount(), project.getGoalAmount());

            // Notify creator
            notificationService.notifyCreatorCampaignFailed(project);

            // Notify every backer that they will be refunded
            // (actual refund processing is Feature #3)
            notifyAllBackers(project, false);
        }
    }

    /**
     * Notify all unique backers of a project.
     * Each notification is @Async so this loop is non-blocking.
     *
     * @param funded true = campaign funded, false = campaign failed
     */
    private void notifyAllBackers(Project project, boolean funded) {
        List<Donation> donations = donationRepository
                .findByProject_IdAndPaymentStatus(project.getId(), PaymentStatus.SUCCESS);

        // Deduplicate — one notification per backer even if they donated multiple times
        donations.stream()
                .map(Donation::getBacker)
                .distinct()
                .forEach(backer -> {
                    if (funded) {
                        notificationService.notifyBackerCampaignFunded(backer, project);
                    } else {
                        notificationService.notifyBackerCampaignFailed(backer, project);
                    }
                });

        log.info("Notified {} backer(s) for project id={} (funded={})",
                donations.size(), project.getId(), funded);
    }
}
