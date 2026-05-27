// src/main/java/Crowdspark/Crowdspark/service/impl/DeadlineSchedulerService.java
// CHANGE from Feature #2: Injected RefundService.
// When project → FAILED, refundService.processRefundsForProject() is called
// immediately after notifying the creator.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.RefundService;
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

    private final ProjectRepository   projectRepository;
    private final DonationRepository  donationRepository;
    private final NotificationService notificationService;
    private final RefundService       refundService;          // ← NEW

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
                log.error("Deadline scheduler error for project id={}: {}",
                        project.getId(), e.getMessage(), e);
            }
        }

        log.info("Deadline scheduler: done.");
    }

    private void processSingle(Project project) {
        boolean goalReached = project.getCurrentAmount() >= project.getGoalAmount();

        if (goalReached) {
            // ── FUNDED ────────────────────────────────────────────────────────
            project.setStatus(ProjectStatus.FUNDED);
            projectRepository.save(project);
            log.info("Project id={} \"{}\" → FUNDED (raised {} of {})",
                    project.getId(), project.getTitle(),
                    project.getCurrentAmount(), project.getGoalAmount());

            notificationService.notifyCreatorCampaignFunded(project);
            notifyAllBackers(project, true);

        } else {
            // ── FAILED ────────────────────────────────────────────────────────
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
            log.info("Project id={} \"{}\" → FAILED (raised {} of {})",
                    project.getId(), project.getTitle(),
                    project.getCurrentAmount(), project.getGoalAmount());

            notificationService.notifyCreatorCampaignFailed(project);
            notifyAllBackers(project, false);

            // ── NEW: trigger automatic refunds for all backers ────────────────
            refundService.processRefundsForProject(project);
        }
    }

    private void notifyAllBackers(Project project, boolean funded) {
        List<Donation> donations = donationRepository
                .findByProject_IdAndPaymentStatus(project.getId(), PaymentStatus.SUCCESS);

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
