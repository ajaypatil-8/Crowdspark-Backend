package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.ProjectService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")   // FIX: added /api prefix to match SecurityConfig rules
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;   // FIX: needed to resolve username → userId

    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/create")
    public Long createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal String username   // FIX: JWT principal = username string
    ) {
        User creator = userService.getByUsername(username);  // FIX: lookup userId properly
        return projectService.createProject(request, creator.getId());
    }

    @GetMapping("/feed")
    public List<ProjectFeedResponse> getFeed() {
        return projectService.getProjectFeed();
    }

    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/creator/projects")
    public List<CreatorProjectResponse> getCreatorProjects(
            @AuthenticationPrincipal String username   // FIX: JWT principal = username string
    ) {
        User creator = userService.getByUsername(username);  // FIX: lookup userId properly
        return projectService.getCreatorProjects(creator.getId());
    }

    @GetMapping("/{id}")
    public ProjectFullDetailsResponse getProjectDetails(@PathVariable Long id) {
        return projectService.getProjectDetails(id);
    }

}
