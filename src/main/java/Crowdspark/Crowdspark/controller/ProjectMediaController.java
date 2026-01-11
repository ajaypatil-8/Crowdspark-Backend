package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AddProjectMediaRequest;
import Crowdspark.Crowdspark.dto.ProjectMediaResponse;
import Crowdspark.Crowdspark.service.ProjectMediaService;
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
public class ProjectMediaController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectMediaController.class);

    private final ProjectMediaService projectMediaService;

    @PreAuthorize("hasAnyRole('ADMIN','CREATOR')")
    @PostMapping("/{projectId}/media")
    public ResponseEntity<ProjectMediaResponse> addMedia(
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMediaRequest request
    ) {
        ProjectMediaResponse response = projectMediaService.addMedia(projectId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("Media added: id={}, projectId={}, type={}", response.getId(), response.getProjectId(), response.getType());

        return ResponseEntity.created(location).body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN','CREATOR')")
    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable Long mediaId) {
        projectMediaService.deleteMedia(mediaId);
        logger.info("Media deleted: id={}", mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/media")
    public ResponseEntity<List<ProjectMediaResponse>> getMedia(@PathVariable Long projectId) {
        List<ProjectMediaResponse> media = projectMediaService.getProjectMedia(projectId);
        return ResponseEntity.ok(media);
    }
}
