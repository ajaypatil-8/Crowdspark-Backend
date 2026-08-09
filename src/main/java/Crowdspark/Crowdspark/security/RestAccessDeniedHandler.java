package Crowdspark.Crowdspark.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Feature #35 — Security response headers (found while auditing this area).
 *
 * Returns a JSON 403 matching this app's normal ApiResponse shape, the same
 * shape GlobalExceptionHandler.handleAccessDenied() already returns for
 * AccessDeniedException thrown from @PreAuthorize checks at the controller
 * level.
 *
 * BUG FIX: without this, that consistency only held for @PreAuthorize-style
 * denials. SecurityConfig also has two URL-pattern role rules
 * (.hasRole("CREATOR"), "/api/v1/admin/**".hasRole("ADMIN")) — those reject
 * the request inside the security filter chain itself, before it ever
 * reaches a controller, so GlobalExceptionHandler's @ExceptionHandler never
 * saw them. Without an explicit AccessDeniedHandler here, Spring Security
 * falls back to its own default handling, which does NOT produce this app's
 * ApiResponse shape — a non-admin hitting /api/v1/admin/** got a
 * differently-shaped error body than every other 403 in the app, which could
 * break frontend error-handling code that expects ApiResponse consistently.
 *
 * Mirrors RestAuthenticationEntryPoint's exact pattern (same manually-
 * constructed ObjectMapper, same response-writing approach) for consistency
 * with that sibling handler, rather than introducing a third way of doing
 * the same thing.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                        HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", "You don't have permission to access this",
                "status",  403
        );

        mapper.writeValue(response.getOutputStream(), body);
    }
}
