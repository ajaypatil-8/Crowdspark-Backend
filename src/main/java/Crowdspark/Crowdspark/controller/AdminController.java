// src/main/java/Crowdspark/Crowdspark/controller/AdminController.java
// CHANGE: Added 3 payout endpoints at the bottom

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.AdminKycAction;
import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.ContactMessageReplyRequest;
import Crowdspark.Crowdspark.dto.ContactMessageResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.PayoutResponse;
import Crowdspark.Crowdspark.dto.RejectProjectRequest;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.AdminService;
import Crowdspark.Crowdspark.service.ContactMessageService;
import Crowdspark.Crowdspark.service.KycService;
import Crowdspark.Crowdspark.service.PayoutService;
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
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService          adminService;
    private final KycService            kycService;
    private final UserService           userService;
    private final ContactMessageService contactMessageService;
    private final PayoutService         payoutService;        // ← NEW

    // ─── Projects ─────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<ProjectFullDetailsResponse>> getProjectDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getProjectDetail(id)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/pending")
    public ResponseEntity<ApiResponse<List<AdminProjectListResponse>>> getPendingProjects() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getPendingProjects()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/all")
    public ResponseEntity<ApiResponse<List<AdminProjectListResponse>>> getAllProjects() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAllProjects()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/projects/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProject(@PathVariable Long id) {
        adminService.approveProject(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Project approved successfully").build());
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/pending")
    public ResponseEntity<ApiResponse<List<KycStatusResponse>>> getPendingKyc() {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getPendingKyc()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kyc/{userId}")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getUserKyc(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(kycService.getMyKycStatus(userId)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/kyc/{userId}/approve")
    public ResponseEntity<ApiResponse<KycStatusResponse>> approveKyc(
            @PathVariable Long userId,
            @AuthenticationPrincipal String adminUsername) {
        User admin = userService.getByUsername(adminUsername);
        return ResponseEntity.ok(ApiResponse.ok("KYC approved",
                kycService.approveKyc(userId, admin.getId())));
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/contact-messages")
    public ResponseEntity<ApiResponse<List<ContactMessageResponse>>> getContactMessages() {
        return ResponseEntity.ok(ApiResponse.ok(contactMessageService.getAll()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/contact-messages/{id}/read")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markContactMessageRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Message marked read", contactMessageService.markRead(id)));
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAllUsers()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable Long id) {
        adminService.suspendUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("User suspended").build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("User activated").build());
    }

    // ─── Payouts (NEW) ────────────────────────────────────────────────────────

    /**
     * POST /admin/projects/{id}/payout
     * Initiate payout to creator for a FUNDED project.
     * Deducts platform fee, sends remaining amount to creator's UPI via Razorpay.
     */
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

    /**
     * GET /admin/payouts
     * List all payouts with their current status.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getAllPayouts() {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getAllPayouts()));
    }

    /**
     * GET /admin/projects/{id}/payout
     * Get payout status for a specific project.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/projects/{id}/payout")
    public ResponseEntity<ApiResponse<PayoutResponse>> getProjectPayout(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getPayoutByProject(id)));
    }
}
