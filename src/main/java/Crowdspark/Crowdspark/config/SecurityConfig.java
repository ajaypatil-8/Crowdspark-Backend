// src/main/java/Crowdspark/Crowdspark/config/SecurityConfig.java
// CHANGES FROM FEATURE #26 (API Versioning):
//   • All /api/* routes moved to /api/v1/*
//   • /admin/* moved to /api/v1/admin/*
//   • /auth/* unchanged (auth is version-stable)
// CHANGES FROM FEATURE #10:
//   1. Added .headers() block with: X-Frame-Options, X-Content-Type-Options,
//      HSTS, Referrer-Policy, Content-Security-Policy, Permissions-Policy
//   2. Added all accumulated route rules from Features #1–#9

package Crowdspark.Crowdspark.config;

import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.OAuth2SuccessHandler;
import Crowdspark.Crowdspark.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final JwtAuthenticationFilter     jwtAuthenticationFilter;
    private final OAuth2SuccessHandler        oAuth2SuccessHandler;
    private final RestAuthenticationEntryPoint restAuthEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── CSRF: disabled — pure JWT/stateless API (correct for REST) ──────
            .csrf(csrf -> csrf.disable())

            // ── CORS: configured in CorsConfig.java ──────────────────────────────
            .cors(Customizer.withDefaults())

            // ── Session: stateless for API, IF_REQUIRED for OAuth2 ───────────────
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // ── SECURITY HEADERS ─────────────────────────────────────────────────
            // NEW: Added in Feature #10
            .headers(headers -> headers

                // Prevents browsers from sniffing content type (stops drive-by
                // downloads disguised as images/CSS)
                .contentTypeOptions(Customizer.withDefaults())

                // Prevents the API responses from being embedded in <iframe>
                // (protects against clickjacking)
                .frameOptions(frame -> frame.deny())

                // HSTS: browsers remember to use HTTPS for 1 year
                // (only active in production over HTTPS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))

                // Referrer: only send origin, not full URL, on cross-origin requests
                .referrerPolicy(rp -> rp.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                // Content-Security-Policy: restrict resource origins
                // — script-src: only our domain + Razorpay checkout
                // — connect-src: API + Razorpay + Cloudinary
                // — img-src: our domain + Cloudinary CDN + data URIs for base64
                // — frame-src: Razorpay checkout iframe
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' https://checkout.razorpay.com; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "connect-src 'self' https://api.razorpay.com https://res.cloudinary.com; " +
                    "img-src 'self' data: blob: https://res.cloudinary.com; " +
                    "font-src 'self' data:; " +
                    "frame-src https://api.razorpay.com; " +
                    "object-src 'none'; " +
                    "base-uri 'self'"
                ))

                // Permissions-Policy: disable unused browser features
                .permissionsPolicy(pp -> pp.policy(
                    "camera=(), microphone=(), geolocation=(), payment=(), " +
                    "usb=(), bluetooth=(), accelerometer=(), gyroscope=()"
                ))
            )

            // ── AUTHORIZATION ─────────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // OAuth2
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                // Swagger / OpenAPI (Feature #8)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/api-docs.yaml"
                ).permitAll()

                // Public auth endpoints
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/refresh",
                    "/auth/verify-email",
                    "/auth/reset-password",
                    "/auth/forgot-password"
                ).permitAll()

                // Public project browsing
                .requestMatchers(
                    "/api/v1/projects/feed",
                    "/api/v1/projects/explore",
                    "/api/v1/categories",
                    "/api/v1/contact/messages"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/rewards").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/updates").permitAll()   // Feature #5
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/comments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/funding-stream").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/view").permitAll()
                .requestMatchers("/auth/totp/verify-login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/followers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/following").permitAll()
                .requestMatchers("/api/v1/feed/followed").authenticated()
                .requestMatchers("/api/v1/users/*/follow").authenticated()
                .requestMatchers("/api/v1/users/*/follow/status").authenticated()

                // Authenticated-only auth actions
                .requestMatchers("/auth/send-verification-email").authenticated()

                // Creator OTP (any authenticated user)
                .requestMatchers(
                    "/api/v1/creator/send-otp",
                    "/api/v1/creator/verify-otp"
                ).authenticated()

                // Creator role required
                .requestMatchers(
                    "/api/v1/creator/submit-kyc",
                    "/api/v1/creator/upload-kyc-doc",
                    "/api/v1/creator/kyc-status",
                    "/api/v1/projects/create",
                    "/api/v1/projects/creator/**"
                ).hasRole("CREATOR")

                // Authenticated user areas
                .requestMatchers("/api/v1/backer/**").authenticated()
                .requestMatchers("/api/v1/notifications/**").authenticated()
                .requestMatchers("/api/v1/payment/**").authenticated()            // Feature #1
                .requestMatchers("/api/v1/users/saved/**").authenticated()        // Feature #7

                // Admin only
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            // ── EXCEPTION HANDLING ────────────────────────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthEntryPoint))

            // ── OAUTH2 ────────────────────────────────────────────────────────────
            .oauth2Login(oauth -> oauth
                .loginPage("/oauth2/authorization/github")
                .successHandler(oAuth2SuccessHandler)
                .failureHandler((request, response, exception) -> {
                    String msg = URLEncoder.encode(
                            exception.getMessage(), StandardCharsets.UTF_8);
                    response.sendRedirect(frontendUrl + "/login?error=" + msg);
                }))

            // ── JWT FILTER ────────────────────────────────────────────────────────
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
