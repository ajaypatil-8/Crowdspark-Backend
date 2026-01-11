package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.LoginRequest;
import Crowdspark.Crowdspark.dto.LoginResponse;
import Crowdspark.Crowdspark.dto.RegisterRequest;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.service.AuthService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

        User user = authService.login(
                request.getIdentifier(),
                request.getPassword()
        );

        String accessToken = jwtUtil.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());

        logger.info("User login success: id={}, username={}", user.getId(), user.getUsername());

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                refreshToken.getToken()
        ));
    }


    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestParam String refreshToken) {

        RefreshToken oldToken = refreshTokenService.validate(refreshToken);

        User user = userService.getById(oldToken.getUserId());


        refreshTokenService.revoke(oldToken.getToken());
        RefreshToken newToken = refreshTokenService.create(user.getId());

        String newAccessToken = jwtUtil.generateAccessToken(user);

        logger.info("Refresh token rotated for userId={}", user.getId());

        return ResponseEntity.ok(new LoginResponse(
                newAccessToken,
                newToken.getToken()
        ));
    }


    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {

        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AuthException("Not authenticated");
        }

        String principalName =
                SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userService.getByUsername(principalName);

        // revoke all refresh tokens
        refreshTokenService.revokeAll(user.getId());

        logger.info(
                "User logged out (all refresh tokens revoked): id={}, username={}",
                user.getId(),
                user.getUsername()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Logged out successfully"
                )
        );
    }

}

