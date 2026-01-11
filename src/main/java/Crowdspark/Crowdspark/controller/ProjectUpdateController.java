package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateProjectUpdateRequest;
import Crowdspark.Crowdspark.dto.ProjectUpdateResponse;
import Crowdspark.Crowdspark.service.ProjectUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/updates")
@RequiredArgsConstructor
public class ProjectUpdateController {

    private final ProjectUpdateService projectUpdateService;


    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping
    public ProjectUpdateResponse addUpdate(
            @PathVariable Long projectId,
            @RequestBody CreateProjectUpdateRequest request
    ) {
        return projectUpdateService.addUpdate(projectId, request);
    }



    @GetMapping
    public List<ProjectUpdateResponse> getUpdates(
            @PathVariable Long projectId
    ) {
        return projectUpdateService.getProjectUpdates(projectId);
    }
}
