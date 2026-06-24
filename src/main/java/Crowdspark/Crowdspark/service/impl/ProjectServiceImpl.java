// src/main/java/Crowdspark/Crowdspark/service/impl/ProjectServiceImpl.java
// CHANGES:
//   exploreProjects() now routes to FTS query when keyword is present,
//   falls back to JPQL query for no-keyword browsing (better performance).
//   Added ENDING_SOON sort option.
//   Added minGoal/maxGoal in-memory filtering.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
import Crowdspark.Crowdspark.dto.ExploreRequest;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository    projectRepository;
    private final UserRepository       userRepository;
    private final CategoryRepository   categoryRepository;
    private final RewardTierRepository rewardTierRepository;
    private final DonationRepository   donationRepository;

    // Statuses publicly viewable on the detail page
    private static final Set<ProjectStatus> VIEWABLE = Set.of(
            ProjectStatus.APPROVED, ProjectStatus.FUNDED,
            ProjectStatus.FAILED,   ProjectStatus.CLOSED
    );

    // ── createProject ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = {"exploreFeed", "projectDetails"}, allEntries = true)
    public Long createProject(CreateProjectRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
        if (categories.isEmpty()) throw new RuntimeException("Invalid categories");

        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setShortDescription(request.getShortDescription());
        project.setFullDescription(request.getFullDescription());
        project.setLocation(request.getLocation());
        project.setGoalAmount(request.getGoalAmount());
        project.setDeadline(request.getDeadline());
        project.setStatus(ProjectStatus.PENDING);
        project.setCreator(creator);
        project.setCategories(categories);

        boolean hasThumbnail = false;
        for (CreateProjectRequest.ProjectMediaRequest m : request.getMedia()) {
            ProjectMedia media = new ProjectMedia();
            media.setMediaUrl(m.getMediaUrl());
            media.setMediaType(m.getMediaType());
            media.setUsage(m.getUsage());
            media.setDisplayOrder(m.getDisplayOrder());
            media.setProject(project);
            if (m.getUsage() == MediaUsage.THUMBNAIL) hasThumbnail = true;
            project.getMedia().add(media);
        }
        if (!hasThumbnail) throw new RuntimeException("Project must have at least one THUMBNAIL image");

        Project saved = projectRepository.save(project);

        if (request.getRewardTiers() != null) {
            for (RewardTierRequest t : request.getRewardTiers()) {
                RewardTier tier = new RewardTier();
                tier.setTitle(t.getTitle());
                tier.setDescription(t.getDescription());
                tier.setMinimumAmount(t.getMinimumAmount());
                tier.setProject(saved);
                rewardTierRepository.save(tier);
            }
        }
        return saved.getId();
    }

    // ── getProjectFeed ────────────────────────────────────────────────────────

    @Override
    public List<ProjectFeedResponse> getProjectFeed() {
        return projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED)
                .stream().map(this::toFeedResponse).toList();
    }

    // ── getCreatorProjects ────────────────────────────────────────────────────

    @Override
    public List<CreatorProjectResponse> getCreatorProjects(Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));
        return projectRepository.findByCreatorOrderByCreatedAtDesc(creator)
                .stream().map(p -> {
                    String thumb = p.getMedia().stream()
                            .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                            .map(ProjectMedia::getMediaUrl).findFirst().orElse(null);
                    return CreatorProjectResponse.builder()
                            .id(p.getId()).title(p.getTitle())
                            .thumbnailUrl(thumb)
                            .goalAmount(p.getGoalAmount())
                            .currentAmount(p.getCurrentAmount())
                            .status(p.getStatus().name())
                            .rejectionReason(p.getRejectionReason())
                            .createdAt(p.getCreatedAt())
                            .deadline(p.getDeadline())
                            .build();
                }).toList();
    }

    // ── getProjectDetails ─────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "projectDetails", key = "#projectId")
    public ProjectFullDetailsResponse getProjectDetails(Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (!VIEWABLE.contains(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not available");
        }

        String categoryName = p.getCategories().isEmpty() ? null
                : p.getCategories().get(0).getName();

        String thumbnail = null;
        List<String> previewVideos = new ArrayList<>(), galleryImages = new ArrayList<>(),
                storyImages = new ArrayList<>();
        for (ProjectMedia m : p.getMedia()) {
            if (m.getUsage() == MediaUsage.THUMBNAIL)     thumbnail = m.getMediaUrl();
            if (m.getUsage() == MediaUsage.CARD_VIDEO)    previewVideos.add(m.getMediaUrl());
            if (m.getUsage() == MediaUsage.GALLERY_IMAGE) galleryImages.add(m.getMediaUrl());
            if (m.getUsage() == MediaUsage.STORY_IMAGE)   storyImages.add(m.getMediaUrl());
        }

        int fundedPct = p.getGoalAmount() > 0
                ? (int) ((p.getCurrentAmount() / p.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), p.getDeadline());
        long backers  = donationRepository.countByProject_IdAndPaymentStatus(
                projectId, PaymentStatus.SUCCESS);

        List<RewardTierResponse> rewards = rewardTierRepository.findByProject_Id(projectId)
                .stream().map(t -> RewardTierResponse.builder()
                        .id(t.getId()).title(t.getTitle())
                        .description(t.getDescription())
                        .minimumAmount(t.getMinimumAmount())
                        .build()).toList();

        return ProjectFullDetailsResponse.builder()
                .id(p.getId()).title(p.getTitle())
                .shortDescription(p.getShortDescription())
                .fullDescription(p.getFullDescription())
                .category(categoryName)
                .goalAmount(p.getGoalAmount()).currentAmount(p.getCurrentAmount())
                .fundedPercentage(fundedPct).daysLeft(daysLeft)
                .deadline(p.getDeadline())
                .thumbnailUrl(thumbnail).previewVideos(previewVideos)
                .galleryImages(galleryImages).storyImages(storyImages)
                .rewards(rewards).backersCount(backers)
                .creator(ProjectFullDetailsResponse.CreatorDto.builder()
                        .id(p.getCreator().getId())
                        .username(p.getCreator().getUsername())
                        .profileImage(p.getCreator().getProfileImageUrl())
                        .about(p.getCreator().getAbout()).build())
                .build();
    }

    // ── exploreProjects — UPGRADED WITH FTS ───────────────────────────────────

    @Override
    public Page<ProjectFeedResponse> exploreProjects(ExploreRequest request) {
        String keyword = (request.getKeyword() != null && !request.getKeyword().isBlank())
                ? request.getKeyword().trim() : null;

        String sort = request.getSort() != null
                ? request.getSort().toUpperCase() : "NEWEST";

        Page<Project> projects;

        if (keyword != null) {
            // ── FTS path: keyword present → use PostgreSQL full-text search ──
            // No sort-based Pageable needed — ORDER BY is in the native query
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
            projects = projectRepository.searchWithFts(
                    request.getCategoryId(), keyword, sort, pageable);

        } else {
            // ── Browse path: no keyword → use fast JPQL + in-DB sorting ──────
            Sort springSort = switch (sort) {
                case "MOST_FUNDED", "TRENDING" ->
                        Sort.by(Sort.Direction.DESC, "currentAmount");
                case "ENDING_SOON" ->
                        Sort.by(Sort.Direction.ASC, "deadline");
                default ->
                        Sort.by(Sort.Direction.DESC, "createdAt");
            };
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), springSort);
            projects = projectRepository.findForExplore(
                    request.getCategoryId(), null, pageable);
        }

        // ── Optional in-memory goal range filter ─────────────────────────────
        // Applied after DB query (goal range filtering is rare and low overhead
        // for typical page sizes of 12)
        if (request.getMinGoal() != null || request.getMaxGoal() != null) {
            List<Project> filtered = projects.getContent().stream()
                    .filter(p -> {
                        if (request.getMinGoal() != null && p.getGoalAmount() < request.getMinGoal())
                            return false;
                        if (request.getMaxGoal() != null && p.getGoalAmount() > request.getMaxGoal())
                            return false;
                        return true;
                    }).toList();

            projects = new PageImpl<>(filtered, projects.getPageable(), filtered.size());
        }

        return projects.map(this::toFeedResponse);
    }

    // ── Cache eviction ────────────────────────────────────────────────────────

    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void evictProjectCaches() { }

    // ── Shared feed mapper ────────────────────────────────────────────────────

    private ProjectFeedResponse toFeedResponse(Project p) {
        String thumbnail = null, previewVideo = null;
        for (ProjectMedia m : p.getMedia()) {
            if (m.getUsage() == MediaUsage.THUMBNAIL)  thumbnail    = m.getMediaUrl();
            if (m.getUsage() == MediaUsage.CARD_VIDEO) previewVideo = m.getMediaUrl();
        }

        int fundedPct = p.getGoalAmount() > 0
                ? (int) ((p.getCurrentAmount() / p.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), p.getDeadline());
        String cat    = p.getCategories().isEmpty() ? null : p.getCategories().get(0).getName();
        long backers  = donationRepository.countByProject_IdAndPaymentStatus(
                p.getId(), PaymentStatus.SUCCESS);

        return ProjectFeedResponse.builder()
                .id(p.getId()).title(p.getTitle())
                .shortDescription(p.getShortDescription())
                .category(cat)
                .thumbnailUrl(thumbnail).previewVideoUrl(previewVideo)
                .goalAmount(p.getGoalAmount()).currentAmount(p.getCurrentAmount())
                .fundedPercentage(fundedPct).daysLeft((int) daysLeft)
                .backersCount(backers)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(p.getCreator().getId())
                        .username(p.getCreator().getUsername())
                        .profileImage(p.getCreator().getProfileImageUrl())
                        .about(p.getCreator().getAbout())
                        .joinedAt(null).totalProjects(0L).totalBackers(0L)
                        .build())
                .build();
    }
}