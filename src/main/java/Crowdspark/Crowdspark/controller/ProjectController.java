package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AssignCategoriesRequest;
import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.ProjectListResponse;
import Crowdspark.Crowdspark.dto.ProjectResponse;
import Crowdspark.Crowdspark.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;



    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse created = projectService.createProject(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        logger.info("Project created: id={}, title={}, creatorId={}", created.getId(), created.getTitle(), created.getCreatorId());

        return ResponseEntity.created(location).body(created);
    }



    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getApprovedProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<ProjectResponse> projects = projectService.getApprovedProjects(page, size);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/listings")
    public ResponseEntity<List<ProjectListResponse>> getProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(
                projectService.getProjectsForListing(page, size)
        );
    }


    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/my")
    public ResponseEntity<List<ProjectResponse>> getMyProjects() {
        List<ProjectResponse> projects = projectService.getMyProjects();
        return ResponseEntity.ok(projects);
    }



    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<ProjectResponse> approve(@PathVariable Long id) {
        ProjectResponse response = projectService.approveProject(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<ProjectResponse> reject(@PathVariable Long id) {
        ProjectResponse response = projectService.rejectProject(id);
        return ResponseEntity.ok(response);
    }



    @PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
    @PutMapping("/{id}/categories")
    public ResponseEntity<ProjectResponse> assignCategories(
            @PathVariable Long id,
            @Valid @RequestBody AssignCategoriesRequest request
    ) {
        ProjectResponse response = projectService.assignCategories(
                id,
                request.getCategoryIds()
        );
        return ResponseEntity.ok(response);
    }
}
