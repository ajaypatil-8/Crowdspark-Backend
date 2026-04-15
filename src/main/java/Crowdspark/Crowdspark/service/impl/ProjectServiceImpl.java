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
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public Long createProject(CreateProjectRequest request, Long creatorId) {

        // 1. Fetch creator
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        // 2. Fetch categories
        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
        if (categories.isEmpty()) {
            throw new RuntimeException("Invalid categories");
        }

        // 3. Create project
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

        // 4. Attach media
        boolean hasThumbnail = false;

        for (CreateProjectRequest.ProjectMediaRequest mediaReq : request.getMedia()) {

            ProjectMedia media = new ProjectMedia();
            media.setMediaUrl(mediaReq.getMediaUrl());
            media.setMediaType(mediaReq.getMediaType());
            media.setUsage(mediaReq.getUsage());
            media.setDisplayOrder(mediaReq.getDisplayOrder());
            media.setProject(project);

            // FIX: use enum comparison instead of fragile .name().equals()
            if (mediaReq.getUsage() == MediaUsage.THUMBNAIL) {
                hasThumbnail = true;
            }

            project.getMedia().add(media);
        }

        // 5. Validate thumbnail
        if (!hasThumbnail) {
            throw new RuntimeException("Project must have at least one THUMBNAIL image");
        }

        // 6. Save
        Project savedProject = projectRepository.save(project);
        return savedProject.getId();
    }

    @Override
    public List<ProjectFeedResponse> getProjectFeed() {

        List<Project> projects =
                projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED);

        return projects.stream().map(project -> {

            String thumbnail = null;
            String previewVideo = null;

            for (ProjectMedia media : project.getMedia()) {
                if (media.getUsage() == MediaUsage.THUMBNAIL) {
                    thumbnail = media.getMediaUrl();
                }
                if (media.getUsage() == MediaUsage.CARD_VIDEO) {
                    previewVideo = media.getMediaUrl();
                }
            }

            // FIX: guard against division by zero
            int fundedPercent = project.getGoalAmount() > 0
                    ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100)
                    : 0;

            // days left
            long daysLeft = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    project.getDeadline()
            );

            // get first category name
            String categoryName = project.getCategories().isEmpty()
                    ? null
                    : project.getCategories().get(0).getName();

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
                    .backersCount(0L) // will add later
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

        }).toList();
    }

    @Override
    public List<CreatorProjectResponse> getCreatorProjects(Long creatorId) {

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        List<Project> projects =
                projectRepository.findByCreatorOrderByCreatedAtDesc(creator);

        return projects.stream().map(project -> {

            // find thumbnail
            String thumbnail = project.getMedia().stream()
                    .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                    .map(m -> m.getMediaUrl())
                    .findFirst()
                    .orElse(null);

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
    public ProjectFullDetailsResponse getProjectDetails(Long projectId) {

        // FIX: return proper 404 instead of 500 RuntimeException
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        // FIX: return 404 (not 500) for non-approved projects
        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not available");
        }

        // category
        String categoryName = project.getCategories().isEmpty()
                ? null
                : project.getCategories().get(0).getName();

        // media separation
        String thumbnail = null;
        List<String> previewVideos = new ArrayList<>();
        List<String> galleryImages = new ArrayList<>();
        List<String> storyImages = new ArrayList<>();

        for (ProjectMedia media : project.getMedia()) {

            if (media.getUsage() == MediaUsage.THUMBNAIL)
                thumbnail = media.getMediaUrl();

            if (media.getUsage() == MediaUsage.CARD_VIDEO)
                previewVideos.add(media.getMediaUrl());

            if (media.getUsage() == MediaUsage.GALLERY_IMAGE)
                galleryImages.add(media.getMediaUrl());

            if (media.getUsage() == MediaUsage.STORY_IMAGE)
                storyImages.add(media.getMediaUrl());
        }

        // FIX: guard against division by zero
        int fundedPercent = project.getGoalAmount() > 0
                ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100)
                : 0;

        long daysLeft = ChronoUnit.DAYS.between(
                LocalDateTime.now(),
                project.getDeadline()
        );

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
                .creator(ProjectFullDetailsResponse.CreatorDto.builder()
                        .id(project.getCreator().getId())
                        .username(project.getCreator().getUsername())
                        .profileImage(null)
                        .about(null)
                        .build())
                .build();
    }

    @Override
    public List<ProjectFeedResponse> exploreProjects(ExploreRequest req) {
        List<Project> projects = new java.util.ArrayList<>(
                projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED));

        if (req.getCategoryId() != null) {
        projects.removeIf(p -> p.getCategories().stream().noneMatch(c -> c.getId().equals(req.getCategoryId())));
        }

        switch (req.getSort()) {
            case "most_funded" -> projects.sort((a, b) -> Double.compare(b.getCurrentAmount(), a.getCurrentAmount()));
            case "trending"    -> projects.sort((a, b) -> {
                double ra = a.getGoalAmount() > 0 ? a.getCurrentAmount() / a.getGoalAmount() : 0;
                double rb = b.getGoalAmount() > 0 ? b.getCurrentAmount() / b.getGoalAmount() : 0;
                return Double.compare(rb, ra);
            });
            default            -> projects.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        }

        int from = req.getPage() * req.getSize();
        int to   = Math.min(from + req.getSize(), projects.size());
        if (from >= projects.size()) return java.util.List.of();

        return projects.subList(from, to).stream().map(project -> {
            String thumbnail = null;
            String previewVideo = null;
            for (Crowdspark.Crowdspark.entity.ProjectMedia media : project.getMedia()) {
                if (media.getUsage() == Crowdspark.Crowdspark.entity.type.MediaUsage.THUMBNAIL) thumbnail = media.getMediaUrl();
                if (media.getUsage() == Crowdspark.Crowdspark.entity.type.MediaUsage.CARD_VIDEO) previewVideo = media.getMediaUrl();
            }
            int fundedPercent = project.getGoalAmount() > 0
                    ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100) : 0;
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), project.getDeadline());
            String categoryName = project.getCategories().isEmpty() ? null : project.getCategories().get(0).getName();
            return ProjectFeedResponse.builder()
                    .id(project.getId()).title(project.getTitle())
                    .shortDescription(project.getShortDescription())
                    .category(categoryName).thumbnailUrl(thumbnail).previewVideoUrl(previewVideo)
                    .goalAmount(project.getGoalAmount()).currentAmount(project.getCurrentAmount())
                    .fundedPercentage(fundedPercent).daysLeft((int) daysLeft).backersCount(0L)
                    .creator(ProjectFeedResponse.CreatorDto.builder()
                            .id(project.getCreator().getId()).username(project.getCreator().getUsername())
                            .profileImage(null).about(null).joinedAt(null).totalProjects(0L).totalBackers(0L)
                            .build())
                    .build();
        }).toList();
    }
}
