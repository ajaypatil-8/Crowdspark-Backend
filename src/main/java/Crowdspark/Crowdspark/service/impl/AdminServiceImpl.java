package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.AccountStatus;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AdminService;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ModelMapper modelMapper;

    // ─── Projects ─────────────────────────────────────────────────────────────

    @Override
    public List<AdminProjectListResponse> getPendingProjects() {
        return toProjectResponses(projectRepository.findByStatus(ProjectStatus.PENDING));
    }

    @Override
    public List<AdminProjectListResponse> getAllProjects() {
        // Returns all projects sorted newest first
        return toProjectResponses(projectRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }

    private List<AdminProjectListResponse> toProjectResponses(Iterable<Project> projects) {
        return ((List<Project>) (projects instanceof List ? projects :
                ((org.springframework.data.domain.Page<Project>) projects).getContent()))
                .stream()
                .map(project -> {
                    String thumbnail = project.getMedia().stream()
                            .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                            .map(ProjectMedia::getMediaUrl)
                            .findFirst()
                            .orElse(null);

                    return AdminProjectListResponse.builder()
                            .id(project.getId())
                            .title(project.getTitle())
                            .creatorUsername(project.getCreator().getUsername())
                            .creatorEmail(project.getCreator().getEmail())
                            .thumbnailUrl(thumbnail)
                            .goalAmount(project.getGoalAmount())
                            .deadline(project.getDeadline())
                            .createdAt(project.getCreatedAt())
                            .status(project.getStatus().name())
                            .build();
                }).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void approveProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new RuntimeException("Only PENDING projects can be approved. Current status: " + project.getStatus());
        }

        project.setStatus(ProjectStatus.APPROVED);
        project.setApprovedAt(LocalDateTime.now());
        projectRepository.save(project);

        notificationService.notifyCreatorProjectApproved(project);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void rejectProject(Long projectId, String reason) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new RuntimeException("Only PENDING projects can be rejected. Current status: " + project.getStatus());
        }

        project.setStatus(ProjectStatus.REJECTED);
        project.setRejectionReason(reason);
        projectRepository.save(project);

        notificationService.notifyCreatorProjectRejected(project, reason);
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(u -> modelMapper.map(u, UserResponse.class))
                .toList();
    }

    @Override
    @Transactional
    public void suspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setAccountStatus(AccountStatus.SUSPENDED);
        user.setLocked(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setLocked(false);
        userRepository.save(user);
    }
}
