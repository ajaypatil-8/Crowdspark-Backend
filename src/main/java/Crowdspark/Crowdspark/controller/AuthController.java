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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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
    private final EmailService emailService;        // ✅ NEW
    private final OtpRepository otpRepository;      // ✅ NEW — reuse OtpVerification table for email tokens

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        logger.info("User registered: username={}, id={}", response.getUsername(), response.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        User user = authService.login(request.getIdentifier(), request.getPassword());
        String accessToken = jwtUtil.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true).secure(false).path("/").sameSite("Lax").maxAge(60 * 60).build();
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken.getToken())
                .httpOnly(true).secure(false).path("/").sameSite("Lax").maxAge(7 * 24 * 60 * 60).build();

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.ok("Login successful",
                new LoginResponse(accessToken, refreshToken.getToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestParam String refreshToken) {
        RefreshToken oldToken = refreshTokenService.validate(refreshToken);
        User user = userService.getById(oldToken.getUserId());
        refreshTokenService.revoke(oldToken.getToken());
        RefreshToken newToken = refreshTokenService.create(user.getId());
        String newAccessToken = jwtUtil.generateAccessToken(user);
        logger.info("Refresh token rotated for userId={}", user.getId());
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed",
                new LoginResponse(newAccessToken, newToken.getToken())));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getByUsername(username);
        refreshTokenService.revokeAll(user.getId());
        logger.info("User logged out: id={}, username={}", user.getId(), user.getUsername());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Logged out successfully").build());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(user.getId())));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateProfile(user.getId(), request)));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> updateProfileImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("Profile image updated",
                userService.updateProfileImage(user.getId(), file)));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/banner-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> updateBannerImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("Banner image updated",
                userService.updateBannerImage(user.getId(), file)));
    }

    // ─── ✅ NEW: Email Verification ───────────────────────────────────────────

    /**
     * POST /auth/send-verification-email
     * Generates a UUID token, stores in otp_verification table (reused),
     * sends email with verify link. Async so response is instant.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send-verification-email")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);

        if (user.isEmailVerified()) {
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true).message("Email already verified").build());
        }

        // Delete any existing token for this email
        otpRepository.deleteByEmail(user.getEmail());

        // Generate token — UUID used as one-time token (stored in otp field)
        String token = UUID.randomUUID().toString();

        OtpVerification record = OtpVerification.builder()
                .email(user.getEmail())
                .otp(token)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .build();
        otpRepository.save(record);

        // Send async email
        String verifyLink = "http://localhost:3000/verify-email?token=" + token + "&email=" + user.getEmail();
        emailService.sendSimpleEmail(
                user.getEmail(),
                "Verify your CrowdSpark email",
                "Hi " + user.getName() + ",\n\n"
                + "Click the link below to verify your email address:\n\n"
                + verifyLink + "\n\n"
                + "This link expires in 24 hours.\n\n"
                + "If you didn't request this, ignore this email.\n\n"
                + "Team CrowdSpark"
        );

        logger.info("Verification email sent to userId={}, email={}", user.getId(), user.getEmail());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Verification email sent to " + user.getEmail()).build());
    }

    /**
     * GET /auth/verify-email?token=xxx&email=yyy
     * Frontend /verify-email page calls this to confirm the token.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam String token,
            @RequestParam String email) {

        Optional<OtpVerification> recordOpt = otpRepository.findByEmail(email);

        if (recordOpt.isEmpty()) {
            throw new AuthException("Invalid or expired verification link");
        }

        OtpVerification record = recordOpt.get();

        if (!record.getOtp().equals(token)) {
            throw new AuthException("Invalid verification token");
        }

        if (record.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRepository.deleteByEmail(email);
            throw new AuthException("Verification link has expired. Please request a new one.");
        }

        // Mark email verified
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found"));
        user.setEmailVerified(true);
        userService.save(user);

        // Cleanup token
        otpRepository.deleteByEmail(email);

        logger.info("Email verified for userId={}", user.getId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Email verified successfully!").build());
    }
}
