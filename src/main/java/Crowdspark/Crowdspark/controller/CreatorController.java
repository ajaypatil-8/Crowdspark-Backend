package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.KycSubmitRequest;
import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.entity.User;
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

import java.util.Map;

@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
@Tag(name = "Creator KYC", description = "KYC submission and phone OTP verification for creator upgrade")
public class CreatorController {

    private final KycService kycService;
    private final UserService userService;
    private final CloudinaryService cloudinaryService;

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
            @RequestBody VerifyOtpRequest request
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
}