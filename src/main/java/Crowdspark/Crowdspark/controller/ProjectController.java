package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.ProjectService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;

    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal String username
    ) {
        User creator = userService.getByUsername(username);
        Long projectId = projectService.createProject(request, creator.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(projectId));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<ProjectFeedResponse>>> getFeed() {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectFeed()));
    }

    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/creator/projects")
    public ResponseEntity<ApiResponse<List<CreatorProjectResponse>>> getCreatorProjects(
            @AuthenticationPrincipal String username
    ) {
        User creator = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(projectService.getCreatorProjects(creator.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectFullDetailsResponse>> getProjectDetails(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectDetails(id)));
    }

    // Section 3 explore — added here
    @GetMapping("/explore")
    public ResponseEntity<ApiResponse<List<ProjectFeedResponse>>> explore(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "12") int size
    ) {
        ExploreRequest req = ExploreRequest.builder()
                .categoryId(categoryId)
                .sort(sort)
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(projectService.exploreProjects(req)));
    }
}
