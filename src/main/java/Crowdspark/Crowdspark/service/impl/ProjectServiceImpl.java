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
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.dto.RewardTierRequest;
import Crowdspark.Crowdspark.dto.RewardTierResponse;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
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

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RewardTierRepository rewardTierRepository;

    @Override
    @Transactional
    @CacheEvict(value = {"exploreFeed", "projectDetails"}, allEntries = true)
    public Long createProject(CreateProjectRequest request, Long creatorId) {

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
        if (categories.isEmpty()) {
            throw new RuntimeException("Invalid categories");
        }

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

        for (CreateProjectRequest.ProjectMediaRequest mediaReq : request.getMedia()) {
            ProjectMedia media = new ProjectMedia();
            media.setMediaUrl(mediaReq.getMediaUrl());
            media.setMediaType(mediaReq.getMediaType());
            media.setUsage(mediaReq.getUsage());
            media.setDisplayOrder(mediaReq.getDisplayOrder());
            media.setProject(project);

            if (mediaReq.getUsage() == MediaUsage.THUMBNAIL) {
                hasThumbnail = true;
            }
            project.getMedia().add(media);
        }

        if (!hasThumbnail) {
            throw new RuntimeException("Project must have at least one THUMBNAIL image");
        }

        Project saved = projectRepository.save(project);

        // Save reward tiers if provided
        if (request.getRewardTiers() != null && !request.getRewardTiers().isEmpty()) {
            for (RewardTierRequest tierReq : request.getRewardTiers()) {
                RewardTier tier = new RewardTier();
                tier.setTitle(tierReq.getTitle());
                tier.setDescription(tierReq.getDescription());
                tier.setMinimumAmount(tierReq.getMinimumAmount());
                tier.setProject(saved);
                rewardTierRepository.save(tier);
            }
        }

        return saved.getId();
    }

    @Override
    @Cacheable(value = "exploreFeed", key = "'feed'")
    public List<ProjectFeedResponse> getProjectFeed() {
        List<Project> projects =
                projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED);
        return projects.stream().map(this::toFeedResponse).toList();
    }

    @Override
    public List<CreatorProjectResponse> getCreatorProjects(Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        return projectRepository.findByCreatorOrderByCreatedAtDesc(creator)
                .stream().map(project -> {
                    String thumbnail = project.getMedia().stream()
                            .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                            .map(ProjectMedia::getMediaUrl)
                            .findFirst().orElse(null);

                    return CreatorProjectResponse.builder()
                            .id(project.getId())
                            .title(project.getTitle())
                            .thumbnailUrl(thumbnail)
                            .goalAmount(project.getGoalAmount())
                            .currentAmount(project.getCurrentAmount())
                            .status(project.getStatus().name())
                            .rejectionReason(project.getRejectionReason())
                            .createdAt(project.getCreatedAt())
                            .deadline(project.getDeadline())
                            .build();
                }).toList();
    }

    @Override
    @Cacheable(value = "projectDetails", key = "#projectId")
    public ProjectFullDetailsResponse getProjectDetails(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not available");
        }

        String categoryName = project.getCategories().isEmpty()
                ? null : project.getCategories().get(0).getName();

        String thumbnail = null;
        List<String> previewVideos = new ArrayList<>();
        List<String> galleryImages = new ArrayList<>();
        List<String> storyImages = new ArrayList<>();

        for (ProjectMedia media : project.getMedia()) {
            if (media.getUsage() == MediaUsage.THUMBNAIL)     thumbnail = media.getMediaUrl();
            if (media.getUsage() == MediaUsage.CARD_VIDEO)    previewVideos.add(media.getMediaUrl());
            if (media.getUsage() == MediaUsage.GALLERY_IMAGE) galleryImages.add(media.getMediaUrl());
            if (media.getUsage() == MediaUsage.STORY_IMAGE)   storyImages.add(media.getMediaUrl());
        }

        int fundedPercent = project.getGoalAmount() > 0
                ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), project.getDeadline());

        List<RewardTier> tiers = rewardTierRepository.findByProject_Id(projectId);
        List<RewardTierResponse> rewards = tiers.stream()
                .map(t -> RewardTierResponse.builder()
                        .id(t.getId())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .minimumAmount(t.getMinimumAmount())
                        .build())
                .toList();

        return ProjectFullDetailsResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .shortDescription(project.getShortDescription())
                .fullDescription(project.getFullDescription())
                .category(categoryName)
                .goalAmount(project.getGoalAmount())
                .currentAmount(project.getCurrentAmount())
                .fundedPercentage(fundedPercent)
                .daysLeft(daysLeft)
                .deadline(project.getDeadline())
                .thumbnailUrl(thumbnail)
                .previewVideos(previewVideos)
                .galleryImages(galleryImages)
                .storyImages(storyImages)
                .rewards(rewards)
                .creator(ProjectFullDetailsResponse.CreatorDto.builder()
                        .id(project.getCreator().getId())
                        .username(project.getCreator().getUsername())
                        .profileImage(null)
                        .about(null)
                        .build())
                .build();
    }

    @Override
    @Cacheable(value = "exploreFeed", key = "#request.categoryId + '-' + #request.keyword + '-' + #request.sort + '-' + #request.page")
    public Page<ProjectFeedResponse> exploreProjects(ExploreRequest request) {

        Sort sort = switch (request.getSort().toUpperCase()) {
            case "MOST_FUNDED" -> Sort.by(Sort.Direction.DESC, "currentAmount");
            case "TRENDING"    -> Sort.by(Sort.Direction.DESC, "currentAmount");
            default            -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        // Pre-process: lowercase + wrap wildcards — avoids lower(bytea) PostgreSQL error
        String keyword = (request.getKeyword() == null || request.getKeyword().isBlank())
                ? null : "%" + request.getKeyword().trim().toLowerCase() + "%";




        return projectRepository
                .findForExplore(request.getCategoryId(), keyword, pageable)
                .map(this::toFeedResponse);
    }

    // ── evict project cache when admin approves/rejects ───────────────────────
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void evictProjectCaches() { }

    // ── shared mapper ─────────────────────────────────────────────────────────
    private ProjectFeedResponse toFeedResponse(Project project) {
        String thumbnail = null;
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
                .backersCount(0L)
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