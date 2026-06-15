// src/main/java/Crowdspark/Crowdspark/controller/TotpController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.TotpService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/totp")
@RequiredArgsConstructor
@Tag(name = "Two-Factor Auth", description = "TOTP 2FA — setup, enable, disable, and login verification")
public class TotpController {

    private final TotpService totpService;
    private final UserService userService;

    // ── GET /auth/totp/setup ─────────────────────────────────────────────
    // Step 1: generate secret + return otpauth URI for QR code display

    @Operation(
        summary  = "Start 2FA setup",
        description = "Generates a TOTP secret and returns the otpauth:// URI to render as QR code. " +
                      "Calling again regenerates the secret (invalidates any previous unconfirmed setup).",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setup(
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        return ResponseEntity.ok(
                ApiResponse.ok(totpService.generateSetup(user.getId())));
    }

    // ── POST /auth/totp/enable ───────────────────────────────────────────
    // Step 2: user scanned QR, submits first code to confirm

    @Operation(
        summary  = "Confirm and enable 2FA",
        description = "User provides their first 6-digit code after scanning the QR. " +
                      "On success, 2FA is active for all future logins.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<Void>> enable(
            @Valid @RequestBody TotpVerifyRequest request,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        totpService.confirmEnable(user.getId(), request.getCode());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Two-factor authentication has been enabled.")
                        .build());
    }

    // ── POST /auth/totp/disable ──────────────────────────────────────────

    @Operation(
        summary  = "Disable 2FA",
        description = "Requires both a valid TOTP code and the account password to prevent unauthorised disablement.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @Valid @RequestBody TotpDisableRequest request,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        totpService.disable(user.getId(), request.getCode(), request.getPassword());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Two-factor authentication has been disabled.")
                        .build());
    }

    // ── POST /auth/totp/verify-login ─────────────────────────────────────

    @Operation(
        summary  = "Complete 2FA login",
        description = "Called after a successful credential check when totpRequired=true. " +
                      "Pass the pendingToken from the login response + the 6-digit authenticator code. " +
                      "Returns full accessToken + refreshToken on success."
    )
    @PostMapping("/verify-login")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyLogin(
            @Valid @RequestBody TotpLoginRequest request,
            HttpServletResponse httpResponse) {

        LoginResponse loginResponse =
                totpService.verifyLoginCode(request.getPendingToken(), request.getCode());

        // Set same HTTP-only cookies as the normal login endpoint
        org.springframework.http.ResponseCookie accessCookie =
                org.springframework.http.ResponseCookie
                        .from("accessToken", loginResponse.getAccessToken())
                        .httpOnly(true).secure(false).path("/")
                        .sameSite("Lax").maxAge(60 * 60).build();

        org.springframework.http.ResponseCookie refreshCookie =
                org.springframework.http.ResponseCookie
                        .from("refreshToken", loginResponse.getRefreshToken())
                        .httpOnly(true).secure(false).path("/")
                        .sameSite("Lax").maxAge(7 * 24 * 60 * 60).build();

        httpResponse.addHeader("Set-Cookie", accessCookie.toString());
        httpResponse.addHeader("Set-Cookie", refreshCookie.toString());

        return ResponseEntity.ok(
                ApiResponse.ok("Login successful", loginResponse));
    }
}
