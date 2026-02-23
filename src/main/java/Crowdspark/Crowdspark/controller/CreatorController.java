package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.KycSubmitRequest;
import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.CloudinaryService;
import Crowdspark.Crowdspark.service.KycService;
import Crowdspark.Crowdspark.service.UserService;
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
public class CreatorController {

    private final KycService kycService;
    private final UserService userService;
    private final CloudinaryService cloudinaryService;

    // send
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.sendOtp(user.getId()));
    }

    // verify
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(
            @AuthenticationPrincipal String username,
            @RequestBody VerifyOtpRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.verifyOtp(user.getId(), request.getOtp()));
    }

    // upload kyc doc image → returns secure_url + public_id
    // call this for each doc (PAN, aadhaar front, aadhaar back) separately
    // then use returned URLs in submit-kyc
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

    // submit
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/submit-kyc")
    public ResponseEntity<KycStatusResponse> submitKyc(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody KycSubmitRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.submitKyc(user.getId(), request));
    }

    // status
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/kyc-status")
    public ResponseEntity<KycStatusResponse> getMyKycStatus(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(kycService.getMyKycStatus(user.getId()));
    }
}