package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

            if (mediaReq.getUsage().name().equals("THUMBNAIL")) {
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

            // funded %
            int fundedPercent = (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100);

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

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new RuntimeException("Project not available");
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

        int fundedPercent =
                (int)((project.getCurrentAmount() / project.getGoalAmount()) * 100);

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


}
