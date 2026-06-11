// src/main/java/Crowdspark/Crowdspark/service/impl/ProjectMilestoneServiceImpl.java

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.MilestoneRequest;
import Crowdspark.Crowdspark.dto.MilestoneResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMilestone;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectMilestoneRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.ProjectMilestoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMilestoneServiceImpl implements ProjectMilestoneService {

    private static final int MAX_MILESTONES_PER_PROJECT = 20;

    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectRepository          projectRepository;
    private final UserRepository             userRepository;
    private final DonationRepository         donationRepository;
    private final NotificationService        notificationService;

    // ── Get ──────────────────────────────────────────────────────────────────

    @Override
    public List<MilestoneResponse> getMilestones(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return milestoneRepository
                .findByProject_IdOrderBySortOrderAscCreatedAtAsc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MilestoneResponse createMilestone(Long projectId,
                                              MilestoneRequest request,
                                              Long creatorId) {
        Project project = loadAndValidateOwner(projectId, creatorId);

        long existing = milestoneRepository.countByProject_Id(projectId);
        if (existing >= MAX_MILESTONES_PER_PROJECT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_MILESTONES_PER_PROJECT + " milestones per project");
        }

        int order = request.getSortOrder() != null
                ? request.getSortOrder()
                : milestoneRepository.findMaxSortOrder(projectId) + 1;

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProject(project);
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(request.getDescription() != null
                ? request.getDescription().trim() : null);
        milestone.setTargetAmount(request.getTargetAmount());
        milestone.setSortOrder(order);

        ProjectMilestone saved = milestoneRepository.save(milestone);
        log.info("Milestone created: projectId={} milestoneId={} by creator={}",
                projectId, saved.getId(), creatorId);
        return toResponse(saved);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MilestoneResponse updateMilestone(Long projectId, Long milestoneId,
                                              MilestoneRequest request, Long creatorId) {
        loadAndValidateOwner(projectId, creatorId);
        ProjectMilestone milestone = findMilestone(projectId, milestoneId);

        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(request.getDescription() != null
                ? request.getDescription().trim() : null);
        milestone.setTargetAmount(request.getTargetAmount());
        if (request.getSortOrder() != null) {
            milestone.setSortOrder(request.getSortOrder());
        }

        ProjectMilestone saved = milestoneRepository.save(milestone);
        log.info("Milestone updated: milestoneId={} by creator={}", milestoneId, creatorId);
        return toResponse(saved);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteMilestone(Long projectId, Long milestoneId, Long creatorId) {
        loadAndValidateOwner(projectId, creatorId);
        ProjectMilestone milestone = findMilestone(projectId, milestoneId);
        milestoneRepository.delete(milestone);
        log.info("Milestone deleted: milestoneId={} by creator={}", milestoneId, creatorId);
    }

    // ── Complete ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MilestoneResponse completeMilestone(Long projectId, Long milestoneId, Long creatorId) {
        loadAndValidateOwner(projectId, creatorId);
        ProjectMilestone milestone = findMilestone(projectId, milestoneId);

        if (milestone.isCompleted()) {
            // Idempotent — already done, return current state
            return toResponse(milestone);
        }

        milestone.setCompletedAt(LocalDateTime.now());
        ProjectMilestone saved = milestoneRepository.save(milestone);

        log.info("Milestone completed: milestoneId={} projectId={} creator={}",
                milestoneId, projectId, creatorId);

        // Notify all backers asynchronously
        notifyBackers(saved);

        return toResponse(saved);
    }

    // ── Reopen ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MilestoneResponse reopenMilestone(Long projectId, Long milestoneId, Long creatorId) {
        loadAndValidateOwner(projectId, creatorId);
        ProjectMilestone milestone = findMilestone(projectId, milestoneId);

        milestone.setCompletedAt(null);
        ProjectMilestone saved = milestoneRepository.save(milestone);
        log.info("Milestone reopened: milestoneId={} by creator={}", milestoneId, creatorId);
        return toResponse(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Loads the project and verifies that the given user is its creator.
     * Throws 404 if project not found, 403 if not the owner.
     */
    private Project loadAndValidateOwner(Long projectId, Long creatorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getCreator().getId().equals(creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the project creator can manage milestones");
        }
        return project;
    }

    private ProjectMilestone findMilestone(Long projectId, Long milestoneId) {
        return milestoneRepository.findByIdAndProject_Id(milestoneId, projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    /** Notify every backer who made a successful donation to this project. */
    private void notifyBackers(ProjectMilestone milestone) {
        Project project = milestone.getProject();
        List<Donation> donations = donationRepository
                .findByProject_IdAndPaymentStatus(project.getId(), PaymentStatus.SUCCESS);

        donations.stream()
                .map(Donation::getBacker)
                .distinct()
                .forEach(backer ->
                        notificationService.notifyBackerMilestoneCompleted(
                                backer, project, milestone.getTitle()));
    }

    private MilestoneResponse toResponse(ProjectMilestone m) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .projectId(m.getProject().getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .targetAmount(m.getTargetAmount())
                .sortOrder(m.getSortOrder())
                .status(m.isCompleted() ? "COMPLETED" : "PENDING")
                .completedAt(m.getCompletedAt())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
