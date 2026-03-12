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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

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

        if (provider.equals("google")) {
            email      = oauthUser.getAttribute("email");
            name       = oauthUser.getAttribute("name");
            providerId = oauthUser.getAttribute("sub");
        } else if (provider.equals("github")) {
            email      = oauthUser.getAttribute("email");
            name       = oauthUser.getAttribute("name");
            providerId = String.valueOf(oauthUser.getAttribute("id"));
            if (email == null) email = providerId + "@github.oauth";
        } else {
            throw new RuntimeException("Unsupported OAuth provider");
        }

        String finalEmail      = email;
        String finalName       = name;
        String finalProviderId = providerId;

        User user = userService.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setUsername(finalEmail.split("@")[0] + "_" + System.currentTimeMillis());
            newUser.setName(finalName);
            newUser.setProvider(provider.toUpperCase());
            newUser.setProviderId(finalProviderId);
            newUser.setEmailVerified(true);
            newUser.addRole(Role.BACKER);
            return userService.save(newUser);
        });

        user.setLastLoginAt(LocalDateTime.now());
        userService.save(user);

        String accessToken        = jwtUtil.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user.getId());

        String redirect = "http://localhost:3000/oauth-callback"
                + "?token="   + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&refresh=" + URLEncoder.encode(refreshToken.getToken(), StandardCharsets.UTF_8);

        response.sendRedirect(redirect);
    }
}