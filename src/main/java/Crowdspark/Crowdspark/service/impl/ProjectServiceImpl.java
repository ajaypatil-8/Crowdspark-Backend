package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
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

import java.util.List;

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
}
