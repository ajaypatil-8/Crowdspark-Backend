package Crowdspark.Crowdspark.config;

import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth

                        // Public
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/api/projects/feed",
                                "/api/projects/{id}"
                        ).permitAll()


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

                        // Admin only
                        .requestMatchers("/admin/**").hasRole("ADMIN")


                        .anyRequest().authenticated()
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