package Crowdspark.Crowdspark.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Returns a JSON 401 response for unauthenticated REST API calls instead of
 * redirecting to the OAuth login page (which is the default Spring behaviour
 * when oauth2Login() is configured).
 *
 * Without this, the browser fetch() in ProfileContext would follow the 302
 * redirect to /oauth2/authorization/google, encounter a CORS error, and the
 * promise would never resolve cleanly — leaving the dashboard spinner running
 * forever.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", "Unauthorized: " + authException.getMessage(),
                "status",  401
        );

        mapper.writeValue(response.getOutputStream(), body);
    }
}
