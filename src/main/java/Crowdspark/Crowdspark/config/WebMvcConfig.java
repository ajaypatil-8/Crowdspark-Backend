// src/main/java/Crowdspark/Crowdspark/config/WebMvcConfig.java
// Feature #32 — registers MdcUserIdInterceptor. No existing WebMvcConfigurer
// bean was present in this codebase (CorsConfig wires CORS through a plain
// CorsConfigurationSource bean instead, consumed by Spring Security's own
// .cors(...) — a different, non-conflicting mechanism), so this is new.

package Crowdspark.Crowdspark.config;

import Crowdspark.Crowdspark.logging.MdcUserIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final MdcUserIdInterceptor mdcUserIdInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(mdcUserIdInterceptor);
    }
}
