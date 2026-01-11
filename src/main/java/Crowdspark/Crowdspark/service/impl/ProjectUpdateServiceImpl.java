package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateProjectUpdateRequest;
import Crowdspark.Crowdspark.dto.ProjectUpdateResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectUpdate;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.ProjectUpdateRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.ProjectUpdateService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectUpdateServiceImpl implements ProjectUpdateService {

    private final ProjectUpdateRepository projectUpdateRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    /* ================= ADD UPDATE ================= */

    @Override
    public ProjectUpdateResponse addUpdate(
            Long projectId,
            CreateProjectUpdateRequest request
    ) {

        User user = getCurrentUser();

        if (!user.getRoles().contains(Role.CREATOR)) {
            throw new AuthException("Only creators can add updates");
        }


        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        if (!project.getCreatorId().equals(user.getId())) {
            throw new AuthException("Not your project");
        }

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new AuthException("Project is not approved");
        }

        ProjectUpdate update = new ProjectUpdate();
        update.setProjectId(projectId);
        update.setTitle(request.getTitle());
        update.setContent(request.getContent());

        ProjectUpdate saved = projectUpdateRepository.save(update);

        auditLogService.log(
                user.getId(),
                "PROJECT_UPDATE_CREATED",
                "PROJECT",
                projectId
        );

        return modelMapper.map(saved, ProjectUpdateResponse.class);
    }

    /* ================= VIEW UPDATES ================= */

    @Override
    public List<ProjectUpdateResponse> getProjectUpdates(Long projectId) {

        return projectUpdateRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(update -> modelMapper.map(update, ProjectUpdateResponse.class))
                .toList();
    }

    /* ================= HELPER ================= */

    private User getCurrentUser() {
        String username =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));
    }
}
