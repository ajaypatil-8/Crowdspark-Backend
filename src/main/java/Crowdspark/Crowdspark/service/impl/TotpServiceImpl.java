// src/main/java/Crowdspark/Crowdspark/service/impl/TotpServiceImpl.java

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.LoginResponse;
import Crowdspark.Crowdspark.dto.TotpSetupResponse;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.TotpService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpServiceImpl implements TotpService {

    private static final String ISSUER = "CrowdSpark";

    private final UserRepository     userRepository;
    private final JwtUtil            jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder    passwordEncoder;

    // ── Libraries (stateless — no Spring beans needed) ────────────────────
    private final SecretGenerator secretGenerator  = new DefaultSecretGenerator(32);
    private final TimeProvider    timeProvider     = new SystemTimeProvider();
    private final CodeGenerator   codeGenerator    = new DefaultCodeGenerator();
    private final CodeVerifier    codeVerifier     = new DefaultCodeVerifier(codeGenerator, timeProvider);

    // ── Generate setup ────────────────────────────────────────────────────

    @Override
    @Transactional
    public TotpSetupResponse generateSetup(Long userId) {
        User user = loadUser(userId);

        // Generate fresh secret (overwrites any previous unconfirmed secret)
        String secret = secretGenerator.generate();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false); // not active until confirmed
        userRepository.save(user);

        String accountName = user.getEmail();
        // otpauth://totp/ISSUER:ACCOUNT?secret=SECRET&issuer=ISSUER&algorithm=SHA1&digits=6&period=30
        String otpauthUri = String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
            urlEncode(ISSUER), urlEncode(accountName), secret, urlEncode(ISSUER)
        );

        log.info("TOTP setup generated for userId={}", userId);
        return TotpSetupResponse.builder()
                .otpauthUri(otpauthUri)
                .secret(secret)
                .issuer(ISSUER)
                .accountName(accountName)
                .build();
    }

    // ── Confirm enable ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void confirmEnable(Long userId, String code) {
        User user = loadUser(userId);

        if (user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No TOTP setup in progress. Call /auth/totp/setup first.");
        }
        if (!isCodeValid(user.getTotpSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid TOTP code — please check your authenticator app and try again.");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
        log.info("TOTP enabled for userId={}", userId);
    }

    // ── Disable ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void disable(Long userId, String code, String password) {
        User user = loadUser(userId);

        if (!user.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "2FA is not enabled on this account.");
        }
        // Verify current account password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Incorrect password.");
        }
        // Verify current TOTP code
        if (!isCodeValid(user.getTotpSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid TOTP code — please check your authenticator app.");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        log.info("TOTP disabled for userId={}", userId);
    }

    // ── Verify login code ─────────────────────────────────────────────────

    @Override
    @Transactional
    public LoginResponse verifyLoginCode(String pendingToken, String code) {
        // 1. Validate the pending token
        if (!jwtUtil.isTokenValid(pendingToken) || !jwtUtil.isPendingTotpToken(pendingToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Pending token is invalid or expired. Please log in again.");
        }

        Claims claims = jwtUtil.extractClaims(pendingToken);
        Long userId   = Long.parseLong(claims.getSubject());

        User user = loadUser(userId);

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "2FA is not enabled for this account.");
        }

        // 2. Validate the TOTP code
        if (!isCodeValid(user.getTotpSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid authenticator code. Please try again.");
        }

        // 3. Issue full tokens
        String accessToken   = jwtUtil.generateAccessToken(user);
        RefreshToken refresh = refreshTokenService.create(user.getId());
        log.info("TOTP login verified for userId={}", userId);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refresh.getToken())
                .build();
    }

    // ── Code validation ───────────────────────────────────────────────────

    @Override
    public boolean isCodeValid(String secret, String code) {
        try {
            // Allow ±1 time window (30 s each side) to tolerate minor clock drift
            ((DefaultCodeVerifier) codeVerifier).setAllowedTimePeriodDiscrepancy(1);
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.warn("TOTP code validation error: {}", e.getMessage());
            return false;
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
