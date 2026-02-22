package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.service.AuthService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

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


    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/users/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("User registered: username={}, id={}", response.getUsername(), response.getId());

        return ResponseEntity.created(location).body(response);
    }


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
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

        return ResponseEntity.ok(new LoginResponse(accessToken, refreshToken.getToken()));
    }


    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestParam String refreshToken) {
        RefreshToken oldToken = refreshTokenService.validate(refreshToken);
        User user = userService.getById(oldToken.getUserId());

        refreshTokenService.revoke(oldToken.getToken());
        RefreshToken newToken = refreshTokenService.create(user.getId());
        String newAccessToken = jwtUtil.generateAccessToken(user);

        logger.info("Refresh token rotated for userId={}", user.getId());

        return ResponseEntity.ok(new LoginResponse(newAccessToken, newToken.getToken()));
    }


    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {

        // principal is the username String set by JwtAuthenticationFilter
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userService.getByUsername(username);
        refreshTokenService.revokeAll(user.getId());

        logger.info("User logged out: id={}, username={}", user.getId(), user.getUsername());

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }


    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(userService.getProfile(user.getId()));
    }


    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(userService.updateProfile(user.getId(), request));
    }


    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateProfileImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(userService.updateProfileImage(user.getId(), file));
    }


    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/me/banner-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateBannerImage(
            @AuthenticationPrincipal String username,
            @RequestPart("file") MultipartFile file
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(userService.updateBannerImage(user.getId(), file));
    }


    @PreAuthorize("hasRole('BACKER')")
    @PostMapping("/me/upgrade-to-creator")
    public ResponseEntity<UserResponse> upgradeToCreator(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreatorUpgradeRequest request
    ) {
        User user = userService.getByUsername(username);
        UserResponse response = userService.upgradeToCreator(user.getId(), request);

        logger.info("Creator upgrade requested: userId={}", user.getId());

        return ResponseEntity.ok(response);
    }
}