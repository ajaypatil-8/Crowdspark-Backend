package Crowdspark.Crowdspark.config;

import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.OAuth2SuccessHandler;
import Crowdspark.Crowdspark.security.RestAccessDeniedHandler;
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
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final JwtAuthenticationFilter     jwtAuthenticationFilter;
    private final OAuth2SuccessHandler        oAuth2SuccessHandler;
    private final RestAuthenticationEntryPoint restAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    // FIX #14: endpoints reachable with no Bearer token yet (there's nothing to
    // be authenticated with before login/registration succeeds), so they can't
    // be exempted by the "already has a Bearer token" rule below.
    private static final Set<String> CSRF_EXEMPT_PATHS = Set.of(
            "/auth/register", "/auth/login", "/auth/refresh",
            "/auth/verify-email", "/auth/reset-password", "/auth/forgot-password",
            "/auth/totp/verify-login",
            // AUDIT FIX (Feature #4): Razorpay's servers call this directly and
            // can never carry a CSRF token or session cookie — it's
            // authenticated via X-Razorpay-Signature instead (see
            // PaymentServiceImpl.verifyWebhookSignature).
            "/api/v1/payment/webhook"
    );

    // FIX #14: CSRF only matters for requests a browser attaches credentials to
    // automatically (cookies). This API is authenticated almost entirely via
    // Bearer JWTs, which a cross-site page cannot forge — the browser only
    // attaches an Authorization header if same-origin JS explicitly set it.
    // So any request that already carries one is exempted; everything else
    // (i.e. anything that would have to rely on the httpOnly cookies
    // AuthController/TotpController set on login) still needs a valid token.
    private static final RequestMatcher CSRF_EXEMPT = request -> {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return true;
        }
        return CSRF_EXEMPT_PATHS.contains(request.getServletPath());
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ── CSRF: Feature #14 ────────────────────────────────────────────────
                // Was `csrf.disable()` outright. That's correct for a pure Bearer-token
                // API, but this one isn't purely that: /auth/login and
                // /auth/totp/verify-login also set httpOnly accessToken/refreshToken
                // cookies (see AuthController, TotpController). Right now nothing
                // reads those cookies back (JwtAuthenticationFilter only checks the
                // Authorization header), so today they're inert — but the moment
                // anything starts relying on them, disabled CSRF becomes a live
                // vulnerability with no one necessarily remembering to revisit this
                // file. Enabling it now (scoped so it can't affect the working
                // Bearer-token flows — see CSRF_EXEMPT above) closes that gap without
                // changing how the app behaves today.
                .csrf(csrf -> {
                    csrf.spa();
                    csrf.ignoringRequestMatchers(CSRF_EXEMPT);
                })

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

                        // Content-Security-Policy: restrict resource origins for whatever
                        // HTML THIS backend itself serves — in practice, that's just
                        // Swagger UI (/swagger-ui.html) and error pages. A response header
                        // set here has NO effect on the Next.js frontend's own pages: that's
                        // a separate origin/server, and a browser only applies the CSP that
                        // came back with THAT response, not this one. The Razorpay/Cloudinary
                        // allowances below are kept anyway (harmless — Swagger UI doesn't use
                        // them, so they're not restricting anything it needs) in case a future
                        // backend-served page needs them, but they do NOT mean Razorpay
                        // checkout or Cloudinary images loaded by the frontend are protected
                        // by this header. If that's wanted, the frontend needs its own CSP
                        // (Next.js: a middleware.ts or headers() entry in next.config.ts) —
                        // out of scope here since Feature #35 is backend-only.
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
                        // BUG FIX (Features #19/#20): both controllers are written to be
                        // public — ProjectReviewController's GETs explicitly resolve an
                        // optional/nullable caller for anonymous viewers, and
                        // ProjectMilestoneController's getMilestones() is documented
                        // "Public" — but neither was ever added here, so both fell
                        // through to anyRequest().authenticated() and 401'd every
                        // logged-out visitor to a project page.
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/reviews/summary").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/milestones").permitAll()
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

                        // AUDIT FIX (Feature #4): Razorpay's server calls this directly —
                        // it will never have our JWT, only its own X-Razorpay-Signature.
                        // Must come before the broader /api/v1/payment/** rule below
                        // since Spring Security uses first-match-wins.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment/webhook").permitAll()

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
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))

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