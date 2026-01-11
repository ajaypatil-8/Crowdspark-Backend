package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.AddProjectMediaRequest;
import Crowdspark.Crowdspark.dto.ProjectMediaResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.ProjectMediaRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ProjectMediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;


import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMediaServiceImpl implements ProjectMediaService {

    private final ProjectMediaRepository projectMediaRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public ProjectMediaResponse addMedia(Long projectId, AddProjectMediaRequest request) {

        if (SecurityContextHolder.getContext() == null ||
                SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AuthException("Not authenticated");
        }

        String username =
                SecurityContextHolder.getContext().getAuthentication().getName();

        if (username == null) {
            throw new AuthException("Not authenticated");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        if (!user.getRoles().contains(Role.ADMIN)
                && !project.getCreatorId().equals(user.getId())) {
            throw new AccessDeniedException("Not allowed");
        }


        ProjectMedia media = new ProjectMedia();
        media.setProjectId(projectId);
        media.setType(request.getType());
        media.setUrl(request.getUrl());

        ProjectMedia saved = projectMediaRepository.save(media);

        return ProjectMediaResponse.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .type(saved.getType())
                .url(saved.getUrl())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    public void deleteMedia(Long mediaId) {

        ProjectMedia media = projectMediaRepository.findById(mediaId)
                .orElseThrow(() -> new AuthException("Media not found"));

        projectMediaRepository.delete(media);
    }

    @Override
    public List<ProjectMediaResponse> getProjectMedia(Long projectId) {
        return projectMediaRepository.findByProjectId(projectId)
                .stream()
                .map(m -> ProjectMediaResponse.builder()
                        .id(m.getId())
                        .projectId(m.getProjectId())
                        .type(m.getType())
                        .url(m.getUrl())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
    }
}
