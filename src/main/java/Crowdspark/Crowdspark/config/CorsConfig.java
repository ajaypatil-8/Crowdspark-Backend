package Crowdspark.Crowdspark.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    // BUG FIX: was a hardcoded List.of("http://localhost:3000") -- the only
    // one of four places in this codebase that use this exact value NOT
    // reading it from app.frontend.url (SecurityConfig, EmailServiceImpl,
    // and OAuth2SuccessHandler all already do). Once deployed with the real
    // frontend on anything other than localhost:3000, every cross-origin
    // request would fail CORS silently -- the browser blocks it client-side
    // before the request even reaches a controller to log anything.
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedHeaders(List.of("*"));
        /*
         * BUG 7 FIX: Added "PATCH" to allowed methods.
         * Without it, any PATCH request from the frontend would fail at
         * CORS preflight with a 403 before reaching the controller.
         */
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
