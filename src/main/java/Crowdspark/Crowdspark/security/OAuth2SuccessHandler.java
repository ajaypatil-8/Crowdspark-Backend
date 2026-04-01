package Crowdspark.Crowdspark.security;

import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        String email;
        String name;
        String providerId;

        if ("google".equals(provider)) {
            email      = oauthUser.getAttribute("email");
            name       = oauthUser.getAttribute("name");
            providerId = oauthUser.getAttribute("sub");

        } else if ("github".equals(provider)) {
            email      = oauthUser.getAttribute("email");
            name       = oauthUser.getAttribute("name");
            providerId = String.valueOf((Object) oauthUser.getAttribute("id"));

            // GitHub hides email when user marks it private — fetch via API
            if (email == null || email.isBlank()) {
                email = fetchGitHubPrimaryEmail(oauthToken, authentication);
                log.info("Fetched GitHub email via API: {}", email);
            }

            // Last-resort stable fallback (deterministic — won't change on re-login)
            if (email == null || email.isBlank()) {
                email = "gh_" + providerId + "@noemail.crowdspark";
                log.warn("Could not fetch GitHub email, using fallback for providerId={}", providerId);
            }

            // GitHub sometimes omits name — fall back to login handle
            if (name == null || name.isBlank()) {
                name = oauthUser.getAttribute("login");
            }

        } else {
            response.sendRedirect("http://localhost:3000/login?error=unsupported_provider");
            return;
        }

        final String finalEmail      = email;
        final String finalName       = (name != null && !name.isBlank()) ? name : "CrowdSpark User";
        final String finalProviderId = providerId;
        final String finalProvider   = provider.toUpperCase();

        // Look up by provider+providerId first so we never clobber
        // a password-registered account that shares the same email
        User user = userService
                .findByProviderAndProviderId(finalProvider, finalProviderId)
                .orElseGet(() ->
                        userService.findByEmail(finalEmail).orElseGet(() -> {
                            User newUser = new User();
                            newUser.setEmail(finalEmail);
                            newUser.setUsername(generateUniqueUsername(finalName, finalEmail));
                            newUser.setName(finalName);
                            newUser.setProvider(finalProvider);
                            newUser.setProviderId(finalProviderId);
                            newUser.setEmailVerified(true);
                            newUser.addRole(Role.BACKER);
                            log.info("Creating new OAuth2 user: email={}, provider={}", finalEmail, finalProvider);
                            return userService.save(newUser);
                        })
                );

        // Attach provider info if user originally signed up with password
        if (user.getProvider() == null || user.getProvider().isBlank()) {
            user.setProvider(finalProvider);
            user.setProviderId(finalProviderId);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userService.save(user);

        String accessToken        = jwtUtil.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());

        String redirect = "http://localhost:3000/oauth-callback"
                + "?token="   + URLEncoder.encode(accessToken,             StandardCharsets.UTF_8)
                + "&refresh=" + URLEncoder.encode(refreshToken.getToken(),  StandardCharsets.UTF_8);

        log.info("OAuth2 success: userId={}, provider={}", user.getId(), provider);
        response.sendRedirect(redirect);
    }

    // ── Fetch real email from GitHub API ─────────────────────────────────────

    private String fetchGitHubPrimaryEmail(OAuth2AuthenticationToken oauthToken,
                                           Authentication authentication) {
        try {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    authentication.getName()
            );
            if (client == null || client.getAccessToken() == null) return null;

            String accessToken = client.getAccessToken().getTokenValue();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Accept",               "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            RestTemplate rt = new RestTemplate();
            ResponseEntity<List<Map<String, Object>>> resp = rt.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> emails = resp.getBody();
            if (emails == null || emails.isEmpty()) return null;

            // Primary + verified first
            Optional<String> primary = emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary"))
                            && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst();
            if (primary.isPresent()) return primary.get();

            // Any verified email as fallback
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);

        } catch (Exception ex) {
            log.error("Failed to fetch GitHub emails: {}", ex.getMessage());
            return null;
        }
    }


    private String generateUniqueUsername(String name, String email) {
        String base = (name != null && !name.isBlank())
                ? name.toLowerCase().replaceAll("[^a-z0-9]", "")
                : email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", "");

        if (base.isBlank()) base = "user";
        if (base.length() > 12) base = base.substring(0, 12);

        String candidate;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
            candidate = base + "_" + suffix;
        } while (userService.existsByUsername(candidate));

        return candidate;
    }
}