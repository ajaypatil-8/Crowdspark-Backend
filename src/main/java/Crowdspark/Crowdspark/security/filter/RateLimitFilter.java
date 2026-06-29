

package Crowdspark.Crowdspark.security.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;



@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // after XssCleanFilter, before JWT
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    @Value("${rate-limit.login.max-requests:5}")
    private int loginMax;
    @Value("${rate-limit.login.window-seconds:60}")
    private int loginWindow;

    @Value("${rate-limit.register.max-requests:10}")
    private int registerMax;
    @Value("${rate-limit.register.window-seconds:3600}")
    private int registerWindow;

    @Value("${rate-limit.forgot-password.max-requests:3}")
    private int forgotMax;
    @Value("${rate-limit.forgot-password.window-seconds:3600}")
    private int forgotWindow;

    @Value("${rate-limit.reset-password.max-requests:5}")
    private int resetMax;
    @Value("${rate-limit.reset-password.window-seconds:3600}")
    private int resetWindow;

    @Value("${rate-limit.send-otp.max-requests:3}")
    private int otpMax;
    @Value("${rate-limit.send-otp.window-seconds:300}")
    private int otpWindow;

    // ── Endpoint → RateLimit map ──────────────────────────────────────────────
    // Built lazily after @Value injection is complete
    private Map<String, RateLimit> limits;

    private Map<String, RateLimit> getLimits() {
        if (limits == null) {
            limits = new LinkedHashMap<>();
            limits.put("/auth/login",               new RateLimit("login",    loginMax,    loginWindow));
            limits.put("/auth/register",             new RateLimit("register", registerMax, registerWindow));
            limits.put("/auth/forgot-password",      new RateLimit("forgot",   forgotMax,   forgotWindow));
            limits.put("/auth/reset-password",       new RateLimit("reset",    resetMax,    resetWindow));
            limits.put("/api/creator/send-otp",      new RateLimit("otp",      otpMax,      otpWindow));
        }
        return limits;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String path   = request.getServletPath();
        String method = request.getMethod();

        // Only rate-limit POST requests to specific paths
        if (!"POST".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        RateLimit limit = getLimits().get(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip  = extractClientIp(request);
        String key = "rate:" + limit.tag() + ":" + ip;

        try {
            Long count = redis.opsForValue().increment(key);

            if (count == null) {
                // Redis returned null unexpectedly — fail open
                chain.doFilter(request, response);
                return;
            }

            // Set expiry on the first increment only
            if (count == 1) {
                redis.expire(key, limit.windowSeconds(), TimeUnit.SECONDS);
            }

            long remaining = Math.max(0, limit.maxRequests() - count);

            // Add informational headers to every response
            response.setHeader("X-RateLimit-Limit",     String.valueOf(limit.maxRequests()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Window",    limit.windowSeconds() + "s");

            if (count > limit.maxRequests()) {
                // Get TTL so we can tell the client when to retry
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = ttl != null && ttl > 0 ? ttl : limit.windowSeconds();

                response.setHeader("Retry-After", String.valueOf(retryAfter));
                log.warn("Rate limit exceeded: path={} ip={} count={} limit={}",
                        path, ip, count, limit.maxRequests());

                sendRateLimitResponse(response, path, retryAfter);
                return;
            }

        } catch (Exception e) {
            // Redis is down — fail open so legitimate users aren't locked out
            log.error("RateLimitFilter: Redis error for path={} ip={}: {}", path, ip, e.getMessage());
        }

        chain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts real client IP, respecting X-Forwarded-For for
     * deployments behind Nginx / Cloudflare / load balancers.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; first is the real client
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       String path,
                                       long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());  // 429
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success",     false);
        body.put("status",      429);
        body.put("error",       "Too Many Requests");
        body.put("message",     "Too many attempts. Please try again in "
                + humanReadable(retryAfterSeconds) + ".");
        body.put("retryAfter",  retryAfterSeconds);
        body.put("path",        path);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String humanReadable(long seconds) {
        if (seconds < 60)   return seconds + " seconds";
        if (seconds < 3600) return (seconds / 60) + " minutes";
        return (seconds / 3600) + " hours";
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    private record RateLimit(String tag, int maxRequests, int windowSeconds) {}
}