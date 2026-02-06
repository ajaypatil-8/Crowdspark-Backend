package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProjectRepository projectRepository;

    @Override
    public List<AdminProjectListResponse> getPendingProjects() {

        List<Project> projects =
                projectRepository.findByStatus(ProjectStatus.PENDING);

        return projects.stream().map(project -> {

            // find thumbnail
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
    public void approveProject(Long projectId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new RuntimeException("Only pending projects can be approved");
        }

        project.setStatus(ProjectStatus.APPROVED);
        project.setApprovedAt(LocalDateTime.now());

        projectRepository.save(project);
    }

    @Override
    public void rejectProject(Long projectId, String reason) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new RuntimeException("Only pending projects can be rejected");
        }

        project.setStatus(ProjectStatus.REJECTED);
        project.setRejectionReason(reason);

        projectRepository.save(project);
    }


}
