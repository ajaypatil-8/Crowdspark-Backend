package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/create")
    public Long createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication
    ) {

        Long creatorId = Long.parseLong(authentication.getName());

        return projectService.createProject(request, creatorId);
    }
}
