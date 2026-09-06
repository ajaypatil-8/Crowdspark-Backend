package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreatorWeeklyInsightResponse;
import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.KycSubmitRequest;
import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.entity.CampaignWeeklyInsight;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.CampaignWeeklyInsightRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.CloudinaryService;
import Crowdspark.Crowdspark.service.KycService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/creator")
@RequiredArgsConstructor
@Tag(name = "Creator KYC", description = "KYC submission and phone OTP verification for creator upgrade")
public class CreatorController {

    private final KycService kycService;
    private final UserService userService;
    private final CloudinaryService cloudinaryService;
    private final ProjectRepository projectRepository; // Feature #48
    private final CampaignWeeklyInsightRepository weeklyInsightRepository; // Feature #48

    @Operation(summary = "Send phone OTP", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.sendOtp(user.getId()));
    }

    @Operation(summary = "Verify phone OTP", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.verifyOtp(user.getId(), request.getOtp()));
    }

    @Operation(summary = "Upload a KYC document image",
            description = "Upload a single KYC doc (PAN, Aadhaar front, Aadhaar back) to Cloudinary. Returns secure_url and public_id to use in submit-kyc.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping(value = "/upload-kyc-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadKycDoc(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file
    ) {
        Map<String, String> result = cloudinaryService.uploadFileWithDetails(
                file, "crowdspark/kyc"
        );
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Submit KYC documents", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/submit-kyc")
    public ResponseEntity<KycStatusResponse> submitKyc(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody KycSubmitRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.submitKyc(user.getId(), request));
    }

    @Operation(summary = "Get KYC status", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/kyc-status")
    public ResponseEntity<KycStatusResponse> getMyKycStatus(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.getMyKycStatus(user.getId()));
    }

    /**
     * GET /api/v1/creator/weekly-insights
     * Feature #48 — the latest AI-written weekly summary for each of this
     * creator's own live campaigns (the same text already emailed to them
     * as a digest by CreatorInsightSchedulerService's Monday job).
     */
    @Operation(summary = "Get this creator's latest weekly campaign insights",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/weekly-insights")
    public ResponseEntity<List<CreatorWeeklyInsightResponse>> getWeeklyInsights(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        List<Project> myProjects = projectRepository.findByCreatorOrderByCreatedAtDesc(user);
        if (myProjects.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> projectIds = myProjects.stream().map(Project::getId).toList();
        List<CampaignWeeklyInsight> insights =
                weeklyInsightRepository.findByProject_IdInOrderByWeekStartDesc(projectIds);

        // Latest row per project only — the query returns every week ever
        // stored, newest first, so the first one seen per project id wins.
        Map<Long, CampaignWeeklyInsight> latestByProject = new java.util.LinkedHashMap<>();
        for (CampaignWeeklyInsight insight : insights) {
            latestByProject.putIfAbsent(insight.getProject().getId(), insight);
        }

        List<CreatorWeeklyInsightResponse> response = latestByProject.values().stream()
                .sorted(Comparator.comparing(CampaignWeeklyInsight::getWeekStart).reversed())
                .map(insight -> CreatorWeeklyInsightResponse.builder()
                        .projectId(insight.getProject().getId())
                        .projectTitle(insight.getProject().getTitle())
                        .summary(insight.getSummaryText())
                        .fundedPercent(insight.getFundedPercent())
                        .newBackersThisWeek(insight.getNewBackersThisWeek())
                        .viewsThisWeek(insight.getViewsThisWeek())
                        .weekStart(insight.getWeekStart())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}