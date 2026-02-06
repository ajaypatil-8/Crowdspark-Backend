package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.RejectProjectRequest;
import Crowdspark.Crowdspark.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET all pending projects
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/pending")
    public List<AdminProjectListResponse> getPendingProjects() {
        return adminService.getPendingProjects();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/projects/{id}/approve")
    public String approveProject(@PathVariable Long id) {
        adminService.approveProject(id);
        return "Project approved successfully";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/projects/{id}/reject")
    public String rejectProject(
            @PathVariable Long id,
            @Valid @RequestBody RejectProjectRequest request
    ) {
        adminService.rejectProject(id, request.getReason());
        return "Project rejected successfully";
    }

}
