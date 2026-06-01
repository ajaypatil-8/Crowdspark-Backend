// src/main/java/Crowdspark/Crowdspark/config/OpenApiConfig.java
// NEW FILE

package Crowdspark.Crowdspark.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crowdsparkOpenAPI() {
        // JWT bearer auth scheme — click "Authorize" in Swagger UI,
        // paste the token from /auth/login and all locked endpoints unlock.
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("bearerAuth")
                .description("Paste your JWT access token. Get one from POST /auth/login");

        return new OpenAPI()
                .info(new Info()
                        .title("CrowdSpark API")
                        .description("""
                            ## CrowdSpark Crowdfunding Platform — REST API
                            
                            **Authentication:** Most endpoints require a JWT Bearer token.
                            1. Call `POST /auth/login` to get an access token.
                            2. Click **Authorize** (top right) and paste the token.
                            3. All authenticated endpoints will now work.
                            
                            **Roles:**
                            - `BACKER` — default role after registration
                            - `CREATOR` — after KYC approval (`/api/creator/submit-kyc`)
                            - `ADMIN` — platform administrators only
                            """)
                        .version("v1.0")
                        .contact(new Contact()
                                .name("CrowdSpark Support")
                                .email("dev@crowdspark.in")
                                .url("https://crowdspark.in"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://crowdspark.in/terms")))

                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/crowdspark")
                                .description("Local development"),
                        new Server()
                                .url("https://api.crowdspark.in")
                                .description("Production")))

                // Apply JWT auth globally — endpoints that need it
                // will show a lock icon; public ones won't require it
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme));
    }
}
