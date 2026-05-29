// src/main/java/Crowdspark/Crowdspark/service/impl/CampaignUpdateServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CampaignUpdateRequest;
import Crowdspark.Crowdspark.dto.CampaignUpdateResponse;
import Crowdspark.Crowdspark.entity.CampaignUpdate;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.CampaignUpdateRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.CampaignUpdateService;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignUpdateServiceImpl implements CampaignUpdateService {

    private final CampaignUpdateRepository campaignUpdateRepository;
    private final ProjectRepository        projectRepository;
    private final UserRepository           userRepository;
    private final DonationRepository       donationRepository;
    private final NotificationService      notificationService;

    // Projects in these statuses can receive updates
    private static final Set<ProjectStatus> UPDATABLE = Set.of(
            ProjectStatus.APPROVED, ProjectStatus.FUNDED, ProjectStatus.CLOSED
    );

    @Override
    @Transactional
    public CampaignUpdateResponse createUpdate(Long projectId,
                                               CampaignUpdateRequest request,
                                               Long creatorId) {
        Project project = loadAndValidateProject(projectId, creatorId);
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        CampaignUpdate update = new CampaignUpdate();
        update.setProject(project);
        update.setAuthor(creator);
        update.setTitle(request.getTitle().trim());
        update.setContent(request.getContent().trim());
        update.setImageUrl(request.getImageUrl());
        CampaignUpdate saved = campaignUpdateRepository.save(update);

        log.info("Campaign update posted: project={} updateId={} by creator={}",
                projectId, saved.getId(), creatorId);

        // Notify all backers asynchronously
        notifyBackers(project, saved);

        return toResponse(saved);
    }

    @Override
    public List<CampaignUpdateResponse> getUpdates(Long projectId) {
        // Verify project exists and is publicly viewable
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return campaignUpdateRepository
                .findByProject_IdOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CampaignUpdateResponse editUpdate(Long projectId, Long updateId,
                                              CampaignUpdateRequest request,
                                              Long creatorId) {
        loadAndValidateProject(projectId, creatorId);
        CampaignUpdate update = campaignUpdateRepository
                .findByIdAndProject_Id(updateId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Update not found"));

        if (!update.getAuthor().getId().equals(creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own updates");
        }

        update.setTitle(request.getTitle().trim());
        update.setContent(request.getContent().trim());
        if (request.getImageUrl() != null) update.setImageUrl(request.getImageUrl());

        return toResponse(campaignUpdateRepository.save(update));
    }

    @Override
    @Transactional
    public void deleteUpdate(Long projectId, Long updateId, Long creatorId) {
        loadAndValidateProject(projectId, creatorId);
        CampaignUpdate update = campaignUpdateRepository
                .findByIdAndProject_Id(updateId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Update not found"));

        if (!update.getAuthor().getId().equals(creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own updates");
        }
        campaignUpdateRepository.delete(update);
        log.info("Campaign update deleted: updateId={} project={}", updateId, projectId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Project loadAndValidateProject(Long projectId, Long creatorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getCreator().getId().equals(creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not the creator of this project");
        }
        if (!UPDATABLE.contains(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Updates can only be posted for active or funded campaigns");
        }
        return project;
    }

    private void notifyBackers(Project project, CampaignUpdate update) {
        List<Donation> donations = donationRepository
                .findByProject_IdAndPaymentStatus(project.getId(), PaymentStatus.SUCCESS);

        donations.stream()
                .map(Donation::getBacker)
                .distinct()
                .forEach(backer ->
                        notificationService.notifyBackerCampaignUpdate(backer, project, update.getTitle())
                );

        log.info("Notified {} backer(s) about update \"{}\" for project={}",
                donations.size(), update.getTitle(), project.getId());
    }

    private CampaignUpdateResponse toResponse(CampaignUpdate u) {
        return CampaignUpdateResponse.builder()
                .id(u.getId())
                .projectId(u.getProject().getId())
                .projectTitle(u.getProject().getTitle())
                .authorId(u.getAuthor().getId())
                .authorUsername(u.getAuthor().getUsername())
                .authorProfileImage(u.getAuthor().getProfileImageUrl())
                .title(u.getTitle())
                .content(u.getContent())
                .imageUrl(u.getImageUrl())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
