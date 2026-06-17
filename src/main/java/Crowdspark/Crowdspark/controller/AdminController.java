// src/main/java/Crowdspark/Crowdspark/controller/AdminController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AdminKycAction;
import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ContactMessageReplyRequest;
import Crowdspark.Crowdspark.dto.ContactMessageResponse;
import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.PayoutResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.dto.RejectProjectRequest;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.AdminService;
import Crowdspark.Crowdspark.service.ContactMessageService;
import Crowdspark.Crowdspark.service.KycService;
import Crowdspark.Crowdspark.service.PayoutService;
import Crowdspark.Crowdspark.service.RefundService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Project approval, KYC review, user management, payouts, refunds. ADMIN role required.")
public class AdminController {

    private final AdminService          adminService;
    private final KycService            kycService;
    private final UserService           userService;
    private final ContactMessageService contactMessageService;
    private final PayoutService         payoutService;
    private final RefundService         refundService;
    private final ProjectRepository     projectRepository;

    // ─── Projects ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get project full details", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<ProjectFullDetailsResponse>> getProjectDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getProjectDetail(id)));
    }

    @Operation(summary = "Get pending projects", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/pending")
    public ResponseEntity<ApiResponse<List<AdminProjectListResponse>>> getPendingProjects() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getPendingProjects()));
    }

    @Operation(summary = "Get all projects", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/all")
    public ResponseEntity<ApiResponse<List<AdminProjectListResponse>>> getAllProjects() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAllProjects()));
    }

    @Operation(summary = "Approve a project", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/projects/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProject(@PathVariable Long id) {
        adminService.approveProject(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Project approved successfully").build());
    }

    @Operation(summary = "Reject a project", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/projects/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectProject(
            @PathVariable Long id,
            @Valid @RequestBody RejectProjectRequest request) {
        adminService.rejectProject(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Project rejected").build());
    }

    // ─── KYC ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Get pending KYC submissions", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/pending")
    public ResponseEntity<ApiResponse<List<KycStatusResponse>>> getPendingKyc() {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getPendingKyc()));
    }

    @Operation(summary = "Get KYC details for a user", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/{userId}")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getUserKyc(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getMyKycStatus(userId)));
    }

    @Operation(summary = "Approve KYC", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/kyc/{userId}/approve")
    public ResponseEntity<ApiResponse<KycStatusResponse>> approveKyc(
            @PathVariable Long userId,
            @AuthenticationPrincipal String adminUsername) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(ApiResponse.ok("KYC approved",
                kycService.approveKyc(userId, admin.getId())));
    }

    @Operation(summary = "Reject KYC", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/kyc/{userId}/reject")
    public ResponseEntity<ApiResponse<KycStatusResponse>> rejectKyc(
            @PathVariable Long userId,
            @AuthenticationPrincipal String adminUsername,
            @RequestBody AdminKycAction action) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(ApiResponse.ok("KYC rejected",
                kycService.rejectKyc(userId, admin.getId(), action.getRejectionReason())));
    }

    // ─── Contact Messages ─────────────────────────────────────────────────────

    @Operation(summary = "List all contact messages", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/contact-messages")
    public ResponseEntity<ApiResponse<List<ContactMessageResponse>>> getContactMessages() {
        return ResponseEntity.ok(ApiResponse.ok(contactMessageService.getAll()));
    }

    @Operation(summary = "Mark a contact message as read", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/contact-messages/{id}/read")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markContactMessageRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Message marked read", contactMessageService.markRead(id)));
    }

    @Operation(summary = "Reply to a contact message", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/contact-messages/{id}/reply")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> replyContactMessage(
            @PathVariable Long id,
            @AuthenticationPrincipal String adminUsername,
            @Valid @RequestBody ContactMessageReplyRequest request) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(ApiResponse.ok("Reply sent",
                contactMessageService.reply(id, request, admin.getId())));
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @Operation(summary = "List all users", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAllUsers()));
    }

    @Operation(summary = "Suspend a user", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable Long id) {
        adminService.suspendUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("User suspended").build());
    }

    @Operation(summary = "Activate a user", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("User activated").build());
    }

    // ─── Payouts ──────────────────────────────────────────────────────────────

    @Operation(summary = "Initiate creator payout", description = "Triggers Razorpay payout to creator's UPI for a FUNDED project.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/projects/{id}/payout")
    public ResponseEntity<ApiResponse<PayoutResponse>> initiatePayout(
            @PathVariable Long id,
            @AuthenticationPrincipal String adminUsername) {
        User admin = userService.getByUsername(adminUsername);
        PayoutResponse response = payoutService.initiatePayout(id, admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @Operation(summary = "List all payouts", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getAllPayouts() {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getAllPayouts()));
    }

    @Operation(summary = "Get payout status for a project", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/{id}/payout")
    public ResponseEntity<ApiResponse<PayoutResponse>> getProjectPayout(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getPayoutByProject(id)));
    }

    // ─── Refunds ──────────────────────────────────────────────────────────────

    @Operation(summary = "List project refunds", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/{id}/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getProjectRefunds(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(refundService.getRefundsForProject(id)));
    }

    @Operation(summary = "Retry failed refunds", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/projects/{id}/refunds/retry")
    public ResponseEntity<ApiResponse<Void>> retryProjectRefunds(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        if (project.getStatus() != ProjectStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Refunds can only be retried for FAILED projects. Current status: "
                            + project.getStatus());
        }

        refundService.processRefundsForProject(project);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Refund retry initiated for all pending donations")
                .build());
    }
}