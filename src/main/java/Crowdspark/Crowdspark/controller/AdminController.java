package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AdminKycAction;
import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.RejectProjectRequest;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.AdminService;
import Crowdspark.Crowdspark.service.KycService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final KycService kycService;
    private final UserService userService;



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


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/pending")
    public ResponseEntity<List<KycStatusResponse>> getPendingKyc() {
        return ResponseEntity.ok(kycService.getPendingKyc());
    }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/{userId}")
    public ResponseEntity<KycStatusResponse> getUserKyc(@PathVariable Long userId) {
        return ResponseEntity.ok(kycService.getMyKycStatus(userId));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/kyc/{userId}/approve")
    public ResponseEntity<KycStatusResponse> approveKyc(
            @PathVariable Long userId,
            @AuthenticationPrincipal String adminUsername
    ) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(kycService.approveKyc(userId, admin.getId()));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/kyc/{userId}/reject")
    public ResponseEntity<KycStatusResponse> rejectKyc(
            @PathVariable Long userId,
            @AuthenticationPrincipal String adminUsername,
            @RequestBody AdminKycAction action
    ) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(kycService.rejectKyc(userId, admin.getId(), action.getRejectionReason()));
    }
}