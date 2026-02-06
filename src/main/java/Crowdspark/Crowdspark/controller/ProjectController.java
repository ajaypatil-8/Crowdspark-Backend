package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/create")
    public Long createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication
    ) {

        Long creatorId = Long.parseLong(authentication.getName());

        return projectService.createProject(request, creatorId);
    }

    @GetMapping("/feed")
    public List<ProjectFeedResponse> getFeed() {
        return projectService.getProjectFeed();
    }


    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/creator/projects")
    public List<CreatorProjectResponse> getCreatorProjects(Authentication authentication) {

        Long creatorId = Long.parseLong(authentication.getName());

        return projectService.getCreatorProjects(creatorId);
    }

    @GetMapping("/{id}")
    public ProjectFullDetailsResponse getProjectDetails(@PathVariable Long id) {
        return projectService.getProjectDetails(id);
    }

}
