// src/main/java/Crowdspark/Crowdspark/controller/AuthController.java
// CHANGE: Added @Tag, @Operation, @ApiResponse Swagger annotations
// All method bodies are IDENTICAL to your existing file — only annotations added.

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.OtpVerification;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.OtpRepository;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.service.AuthService;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Authentication", description = "Register, login, token refresh, profile management, email verification and password reset")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Register a new user",
               description = "Creates a new backer account. Email verification required before some features unlock.")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "User created successfully"),
                    @ApiResponse(responseCode = "409", description = "Email or username already taken"),
                    @ApiResponse(responseCode = "400", description = "Validation error") })
    @PostMapping("/register")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        logger.info("User registered: username={}, id={}", response.getUsername(), response.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Crowdspark.Crowdspark.dto.ApiResponse.created(response));
    }

    @Operation(summary = "Login",
               description = "Returns JWT access token + refresh token. Tokens are also set as HTTP-only cookies.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials"),
                    @ApiResponse(responseCode = "403", description = "Account suspended") })
    @PostMapping("/login")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        User user = authService.login(request.getIdentifier(), request.getPassword());
        String accessToken = jwtUtil.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true).secure(false).path("/").sameSite("Lax").maxAge(60 * 60).build();
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken.getToken())
                .httpOnly(true).secure(false).path("/").sameSite("Lax").maxAge(7 * 24 * 60 * 60).build();

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Login successful",
                new LoginResponse(accessToken, refreshToken.getToken())));
    }

    @Operation(summary = "Refresh access token",
               description = "Pass a valid refresh token to get a new access token. Old refresh token is revoked.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "New tokens issued"),
                    @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired") })
    @PostMapping("/refresh")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<LoginResponse>> refresh(
            @RequestParam String refreshToken) {
        RefreshToken oldToken = refreshTokenService.validate(refreshToken);
        User user = userService.getById(oldToken.getUserId());
        refreshTokenService.revoke(oldToken.getToken());
        RefreshToken newToken = refreshTokenService.create(user.getId());
        String newAccessToken = jwtUtil.generateAccessToken(user);
        logger.info("Refresh token rotated for userId={}", user.getId());
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Token refreshed",
                new LoginResponse(newAccessToken, newToken.getToken())));
    }

    @Operation(summary = "Logout", description = "Revokes all refresh tokens for the current user.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<Void>> logout() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getByUsername(username);
        refreshTokenService.revokeAll(user.getId());
        logger.info("User logged out: id={}, username={}", user.getId(), user.getUsername());
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.<Void>builder()
                .success(true).message("Logged out successfully").build());
    }

    @Operation(summary = "Get current user profile",
               description = "Returns full profile of the authenticated user.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Profile returned"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated") })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok(userService.getProfile(user.getId())));
    }

    @Operation(summary = "Update profile",
               description = "Update name, bio, social links, bank/UPI details.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/profile")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Profile updated",
                userService.updateProfile(user.getId(), request)));
    }

    @Operation(summary = "Upload profile image",
               description = "Multipart upload. Image stored on Cloudinary.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<UserResponse>> updateProfileImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Profile image updated",
                userService.updateProfileImage(user.getId(), file)));
    }

    @Operation(summary = "Upload banner image",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/banner-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<UserResponse>> updateBannerImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Banner image updated",
                userService.updateBannerImage(user.getId(), file)));
    }

    @Operation(summary = "Send email verification link",
               description = "Sends a 24-hour verification link to the user's email.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send-verification-email")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<Void>> sendVerificationEmail(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        if (user.isEmailVerified()) {
            return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.<Void>builder()
                    .success(true).message("Email already verified").build());
        }
        otpRepository.deleteByEmail(user.getEmail());
        String token = UUID.randomUUID().toString();
        OtpVerification record = OtpVerification.builder()
                .email(user.getEmail()).otp(token)
                .expiryTime(LocalDateTime.now().plusHours(24)).build();
        otpRepository.save(record);
        String verifyLink = "http://localhost:3000/verify-email?token=" + token + "&email=" + user.getEmail();
        emailService.sendSimpleEmail(user.getEmail(), "Verify your CrowdSpark email",
                "Hi " + user.getName() + ",\n\nVerify here:\n\n" + verifyLink + "\n\nExpires in 24 hours.\n\nTeam CrowdSpark");
        logger.info("Verification email sent to userId={}", user.getId());
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.<Void>builder()
                .success(true).message("Verification email sent to " + user.getEmail()).build());
    }

    @Operation(summary = "Request password reset email",
               description = "Always returns 200 to prevent email enumeration. Sends reset link if email exists.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<String>> forgotPassword(
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email != null && !email.isBlank()) {
            try {
                otpRepository.deleteByEmail(email.trim());
                String token = UUID.randomUUID().toString();
                OtpVerification record = OtpVerification.builder()
                        .email(email.trim()).otp(token)
                        .expiryTime(LocalDateTime.now().plusHours(1)).build();
                otpRepository.save(record);
                String resetLink = "http://localhost:3000/reset-password?token=" + token + "&email=" + email.trim();
                emailService.sendSimpleEmail(email.trim(), "Reset your CrowdSpark password",
                        "Click to reset:\n\n" + resetLink + "\n\nExpires in 1 hour.\n\nTeam CrowdSpark");
                logger.info("Password reset email requested for email={}", email.trim());
            } catch (Exception e) {
                logger.warn("Password reset request failed silently for email={}", email);
            }
        }
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Reset link sent if account exists", "OK"));
    }

    @Operation(summary = "Reset password using token",
               description = "Validates the reset token and updates the password. Revokes all refresh tokens.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Password updated"),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired token") })
    @PostMapping("/reset-password")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<String>> resetPassword(
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        String token = body.get("token");
        String newPassword = body.get("password");
        if (email == null || token == null || newPassword == null
                || email.isBlank() || token.isBlank() || newPassword.isBlank()) {
            throw new AuthException("Missing required fields");
        }
        if (newPassword.length() < 8) throw new AuthException("Password must be at least 8 characters");
        Optional<OtpVerification> recordOpt = otpRepository.findByEmail(email.trim());
        if (recordOpt.isEmpty()) throw new AuthException("Invalid or expired reset link.");
        OtpVerification record = recordOpt.get();
        if (!record.getOtp().equals(token)) throw new AuthException("Invalid reset token.");
        if (record.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRepository.deleteByEmail(email.trim());
            throw new AuthException("Reset link has expired.");
        }
        User user = userService.findByEmail(email.trim()).orElseThrow(() -> new AuthException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.save(user);
        otpRepository.deleteByEmail(email.trim());
        refreshTokenService.revokeAll(user.getId());
        logger.info("Password reset successful for userId={}", user.getId());
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.ok("Password reset successful", "OK"));
    }

    @Operation(summary = "Verify email address",
               description = "Called by the frontend /verify-email page with the token from the email link.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Email verified"),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired token") })
    @GetMapping("/verify-email")
    public ResponseEntity<Crowdspark.Crowdspark.dto.ApiResponse<Void>> verifyEmail(
            @RequestParam String token,
            @RequestParam String email) {
        Optional<OtpVerification> recordOpt = otpRepository.findByEmail(email);
        if (recordOpt.isEmpty()) throw new AuthException("Invalid or expired verification link");
        OtpVerification record = recordOpt.get();
        if (!record.getOtp().equals(token)) throw new AuthException("Invalid verification token");
        if (record.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRepository.deleteByEmail(email);
            throw new AuthException("Verification link has expired.");
        }
        User user = userService.findByEmail(email).orElseThrow(() -> new AuthException("User not found"));
        user.setEmailVerified(true);
        userService.save(user);
        otpRepository.deleteByEmail(email);
        logger.info("Email verified for userId={}", user.getId());
        return ResponseEntity.ok(Crowdspark.Crowdspark.dto.ApiResponse.<Void>builder()
                .success(true).message("Email verified successfully!").build());
    }
}
