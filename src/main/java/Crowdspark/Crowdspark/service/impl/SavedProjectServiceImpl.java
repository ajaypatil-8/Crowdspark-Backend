// src/main/java/Crowdspark/Crowdspark/service/impl/SavedProjectServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.SavedProject;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.SavedProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.SavedProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedProjectServiceImpl implements SavedProjectService {

    private final SavedProjectRepository savedProjectRepository;
    private final ProjectRepository      projectRepository;
    private final UserRepository         userRepository;
    private final DonationRepository     donationRepository;

    @Override
    @Transactional
    public void save(Long userId, Long projectId) {
        if (savedProjectRepository.existsByUser_IdAndProject_Id(userId, projectId)) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        SavedProject sp = new SavedProject();
        sp.setUser(user);
        sp.setProject(project);
        savedProjectRepository.save(sp);
        log.info("User {} saved project {}", userId, projectId);
    }

    @Override
    @Transactional
    public void unsave(Long userId, Long projectId) {
        savedProjectRepository.deleteByUser_IdAndProject_Id(userId, projectId);
        log.info("User {} unsaved project {}", userId, projectId);
    }

    @Override
    @Transactional
    public boolean toggle(Long userId, Long projectId) {
        boolean alreadySaved = savedProjectRepository
                .existsByUser_IdAndProject_Id(userId, projectId);
        if (alreadySaved) {
            unsave(userId, projectId);
            return false;
        } else {
            save(userId, projectId);
            return true;
        }
    }

    @Override
    public List<ProjectFeedResponse> getSaved(Long userId) {
        return savedProjectRepository
                .findByUser_IdOrderBySavedAtDesc(userId)
                .stream()
                .map(sp -> toFeedResponse(sp.getProject()))
                .toList();
    }

    @Override
    public boolean isSaved(Long userId, Long projectId) {
        return savedProjectRepository.existsByUser_IdAndProject_Id(userId, projectId);
    }

    // ── mapper (reuses same pattern as ProjectServiceImpl) ────────────────────
    private ProjectFeedResponse toFeedResponse(Project project) {
        String thumbnail    = null;
        String previewVideo = null;

        for (ProjectMedia media : project.getMedia()) {
            if (media.getUsage() == MediaUsage.THUMBNAIL)  thumbnail    = media.getMediaUrl();
            if (media.getUsage() == MediaUsage.CARD_VIDEO) previewVideo = media.getMediaUrl();
        }

        int fundedPercent = project.getGoalAmount() > 0
                ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), project.getDeadline());
        String categoryName = project.getCategories().isEmpty()
                ? null : project.getCategories().get(0).getName();
        long backersCount = donationRepository.countByProject_IdAndPaymentStatus(
                project.getId(), PaymentStatus.SUCCESS);

        return ProjectFeedResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .shortDescription(project.getShortDescription())
                .category(categoryName)
                .thumbnailUrl(thumbnail)
                .previewVideoUrl(previewVideo)
                .goalAmount(project.getGoalAmount())
                .currentAmount(project.getCurrentAmount())
                .fundedPercentage(fundedPercent)
                .daysLeft((int) daysLeft)
                .backersCount(backersCount)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(project.getCreator().getId())
                        .username(project.getCreator().getUsername())
                        .profileImage(null)
                        .about(null)
                        .joinedAt(null)
                        .totalProjects(0L)
                        .totalBackers(0L)
                        .build())
                .build();
    }
}
