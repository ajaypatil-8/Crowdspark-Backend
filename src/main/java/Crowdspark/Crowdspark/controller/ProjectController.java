package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
import Crowdspark.Crowdspark.dto.ExploreRequest;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.CloudinaryService;
import Crowdspark.Crowdspark.service.ProjectService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService      projectService;
    private final UserService         userService;
    private final CloudinaryService   cloudinaryService;

    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal String username
    ) {
        User creator = userService.getByUsername(username);
        Long projectId = projectService.createProject(request, creator.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(projectId));
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
    public ResponseEntity<ApiResponse<ProjectFullDetailsResponse>> getProjectDetails(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectDetails(id)));
    }

    // GET /api/projects/explore?categoryId=1&keyword=solar&sort=TRENDING&page=0&size=12
    @GetMapping("/explore")
    public ResponseEntity<ApiResponse<Page<ProjectFeedResponse>>> explore(
            @ModelAttribute ExploreRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.exploreProjects(request)));
    }

    /**
     * POST /api/projects/upload-media
     * Uploads image/video to Cloudinary under "crowdspark/projects" folder.
     * Used by Step3Media in the campaign creation wizard.
     * Returns: { secure_url, public_id, resource_type }
     */
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping(value = "/upload-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadMedia(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal String username
    ) {
        Map<String, String> result = cloudinaryService.uploadFileWithDetails(
                file, "crowdspark/projects"
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
