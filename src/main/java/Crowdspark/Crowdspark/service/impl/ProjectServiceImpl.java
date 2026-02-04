package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaType;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.ProjectMediaRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.ProjectService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    @Override
    public ProjectResponse createProject(CreateProjectRequest request) {

        User user = getCurrentUser();

        if (!user.getRoles().contains(Role.CREATOR)) {
            throw new AuthException("Only creators can create projects");
        }

        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setGoalAmount(request.getGoalAmount());
        project.setCurrentAmount(0.0);
        project.setStatus(ProjectStatus.PENDING);
        project.setCreatorId(user.getId());

        Project saved = projectRepository.save(project);

        auditLogService.log(user.getId(), "PROJECT_CREATED", "PROJECT", saved.getId());

        return mapProject(saved);
    }

    @Override
    public List<ProjectResponse> getApprovedProjects(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return projectRepository.findByStatus(ProjectStatus.APPROVED, pageable)
                .stream()
                .map(this::mapProject)
                .toList();
    }

    @Override
    public List<ProjectListResponse> getProjectsForListing(int page, int size) {

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        return projectRepository
                .findByStatus(ProjectStatus.APPROVED, pageable)
                .stream()
                .map(project -> {

                    User creator = userRepository.findById(project.getCreatorId())
                            .orElseThrow(() -> new AuthException("Creator not found"));

                    String thumbnailUrl = projectMediaRepository
                            .findFirstByProjectIdAndType(project.getId(), MediaType.IMAGE)
                            .map(ProjectMedia::getUrl)
                            .orElse(null);

                    return new ProjectListResponse(
                            project.getId(),
                            project.getTitle(),
                            project.getDescription(), // or shortDescription field if you add one
                            thumbnailUrl,
                            creator.getUsername(),
                            project.getGoalAmount(),
                            project.getCurrentAmount(),
                            project.getCreatedAt()
                    );
                })
                .toList();
    }


    @Override
    public List<ProjectResponse> getMyProjects() {
        User user = getCurrentUser();
        return projectRepository.findByCreatorId(user.getId())
                .stream()
                .map(this::mapProject)
                .toList();
    }

    @Override
    public ProjectResponse approveProject(Long projectId) {

        User admin = getCurrentUser();

        if (!admin.getRoles().contains(Role.ADMIN)) {
            throw new AuthException("Only admin can approve projects");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        project.setStatus(ProjectStatus.APPROVED);
        Project saved = projectRepository.save(project);

        auditLogService.log(admin.getId(), "PROJECT_APPROVED", "PROJECT", projectId);

        return mapProject(saved);
    }

    @Override
    public ProjectResponse rejectProject(Long projectId) {

        User admin = getCurrentUser();

        if (!admin.getRoles().contains(Role.ADMIN)) {
            throw new AuthException("Only admin can reject projects");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        project.setStatus(ProjectStatus.REJECTED);
        Project saved = projectRepository.save(project);

        auditLogService.log(admin.getId(), "PROJECT_REJECTED", "PROJECT", projectId);

        return mapProject(saved);
    }

    @Override
    public ProjectResponse assignCategories(Long projectId, List<Long> categoryIds) {

        User user = getCurrentUser();
        Project project;

        if (user.getRoles().contains(Role.ADMIN)) {
            project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new AuthException("Project not found"));
        } else if (user.getRoles().contains(Role.CREATOR)) {
            project = projectRepository.findByIdAndCreatorId(projectId, user.getId())
                    .orElseThrow(() -> new AuthException("Not your project"));
        } else {
            throw new AuthException("Not allowed");
        }

        List<Category> categories = categoryRepository.findAllById(categoryIds);

        project.setCategories(categories);
        Project saved = projectRepository.save(project);

        auditLogService.log(user.getId(), "CATEGORIES_ASSIGNED", "PROJECT", projectId);

        return mapProject(saved);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    private ProjectResponse mapProject(Project project) {

        ProjectResponse response = modelMapper.map(project, ProjectResponse.class);

        response.setCategories(
                project.getCategories()
                        .stream()
                        .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getCreatedAt()))
                        .toList()
        );

        response.setMedia(
                projectMediaRepository.findByProjectId(project.getId())
                        .stream()
                        .map(m -> ProjectMediaResponse.builder()
                                .id(m.getId())
                                .projectId(m.getProjectId())
                                .type(m.getType())
                                .url(m.getUrl())
                                .createdAt(m.getCreatedAt())
                                .build())
                        .toList()
        );

        return response;
    }
}
