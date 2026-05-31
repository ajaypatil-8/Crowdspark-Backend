package Crowdspark.Crowdspark.config;

import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.OAuth2SuccessHandler;
import Crowdspark.Crowdspark.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final RestAuthenticationEntryPoint restAuthEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/verify-email",          // ✅ NEW: public — link in email, no token yet
                                "/api/projects/feed",
                                "/api/projects/{id}",
                                "/api/projects/explore",
                                "/api/categories",
                                "/api/contact/messages",
                                "/auth/reset-password" ,
                                "/auth/forgot-password" // ✅ already correct (CategoryController now at /api/categories)
                        ).permitAll()

                        // ✅ send-verification-email requires auth (user must be logged in)
                        .requestMatchers(
                                "/auth/send-verification-email"
                        ).authenticated()

                        .requestMatchers(
                                "/api/creator/send-otp",
                                "/api/creator/verify-otp"
                        ).authenticated()

                        .requestMatchers(
                                "/api/creator/submit-kyc",
                                "/api/creator/upload-kyc-doc",
                                "/api/creator/kyc-status"
                        ).hasRole("CREATOR")

                        .requestMatchers(
                                "/api/projects/create",
                                "/api/projects/creator/**"
                        ).hasRole("CREATOR")

                        // Section 4 — Backer Dashboard
                        .requestMatchers("/api/backer/**").authenticated()

                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        .requestMatchers("/api/notifications/**").authenticated()

                        .requestMatchers("/api/payment/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/projects/*/rewards").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/projects/*/updates").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/projects/*/comments").permitAll()

                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthEntryPoint)
                )

                .oauth2Login(oauth -> oauth
                        .loginPage("/oauth2/authorization/github")
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            String msg = URLEncoder.encode(
                                    exception.getMessage(), StandardCharsets.UTF_8);
                            response.sendRedirect(frontendUrl + "/login?error=" + msg);
                        })
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
