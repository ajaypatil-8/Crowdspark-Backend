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

    // AUDIT FIX (Feature #8): these two didn't exist before. verify-otp had no
    // rate limit at all, and /auth/totp/verify-login (a brute-forceable 6-digit
    // code, entered mid-login before a full session exists) had neither an
    // entry here NOR any attempt-lockout of its own in TotpServiceImpl.
    @Value("${rate-limit.verify-otp.max-requests:5}")
    private int verifyOtpMax;
    @Value("${rate-limit.verify-otp.window-seconds:300}")
    private int verifyOtpWindow;

    @Value("${rate-limit.totp-verify.max-requests:5}")
    private int totpVerifyMax;
    @Value("${rate-limit.totp-verify.window-seconds:300}")
    private int totpVerifyWindow;

    // Feature #42: public, unauthenticated chatbot -- this is the only
    // reason this filter has anything to do with it. Every other AI
    // endpoint requires a login and gets its own per-creator daily Redis
    // counter inside AiServiceImpl instead; this one has no user id to key
    // on, so it leans on the same IP-based mechanism as everything else here.
    @Value("${rate-limit.support-chat.max-requests:20}")
    private int supportChatMax;
    @Value("${rate-limit.support-chat.window-seconds:3600}")
    private int supportChatWindow;

    // DEPLOYMENT FIX (Render): see the Javadoc on extractClientIp() below for
    // why this exists. Defaults to false so local/Docker-Compose/self-hosted
    // behavior is completely unchanged; set APP_TRUST_PROXY_HEADERS=true only
    // where the platform itself guarantees the app can't be reached except
    // through its proxy (this is true on Render).
    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

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
            // AUDIT FIX (Feature #8/#26): this was "/api/creator/send-otp" —
            // a path left over from before API versioning (Feature #26) moved
            // every /api/** route under /api/v1/**. request.getServletPath()
            // for the real route never matched that stale entry, so send-otp
            // has been completely unlimited since versioning shipped.
            limits.put("/api/v1/creator/send-otp",   new RateLimit("otp",          otpMax,          otpWindow));
            limits.put("/api/v1/creator/verify-otp", new RateLimit("verify-otp",   verifyOtpMax,    verifyOtpWindow));
            limits.put("/auth/totp/verify-login",    new RateLimit("totp-verify", totpVerifyMax,   totpVerifyWindow));
            limits.put("/api/v1/ai/support-chat",    new RateLimit("support-chat", supportChatMax, supportChatWindow));
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
     *
     * BUG FIX (Feature #8): this used to trust X-Forwarded-For / X-Real-IP
     * unconditionally. Since the rate-limit key is "rate:<tag>:<ip>", anyone
     * on the open internet could send a different fake X-Forwarded-For value
     * on every request and get a brand-new bucket each time -- a one-line
     * bypass of the entire login/OTP brute-force protection. These headers
     * are only trustworthy when they were actually set by OUR reverse proxy,
     * which is what request.getRemoteAddr() (the TCP peer Spring Boot itself
     * saw -- not attacker-controllable) being a private/loopback address
     * indicates. A direct connection from the public internet always uses
     * getRemoteAddr() instead, no matter what headers it sent.
     *
     * DEPLOYMENT FIX (Render): the check above assumes "our reverse proxy"
     * always shows up as a private RFC1918 address, which is true for
     * Nginx/Docker Compose/a K8s ingress on our own network, but NOT true on
     * Render. Render's own docs confirm the app only ever sees Render's edge
     * proxy address as the direct peer (e.g. 147.75.x.x) -- a real address,
     * not 10.x/192.168.x/172.16-31.x -- so isTrustedProxyAddress() would
     * always return false there, X-Forwarded-For would never be trusted,
     * and every user behind Render's edge would collapse onto whatever
     * address Render's proxy happens to present, breaking per-user rate
     * limiting (either everyone shares one bucket, or the "IP" is
     * inconsistent across requests). Unlike a self-hosted box, this is safe
     * to work around unconditionally on Render specifically: Render's proxy
     * is the *only* way to reach the app at all (its public port is not
     * directly internet-routable), so there's no scenario where an attacker
     * bypasses it to spoof these headers directly -- hence
     * app.trust-proxy-headers, set only in the Render environment.
     */
    private String extractClientIp(HttpServletRequest request) {
        String directAddr = request.getRemoteAddr();
        if (!trustProxyHeaders && !isTrustedProxyAddress(directAddr)) {
            return directAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; first is the real client
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return directAddr;
    }

    /**
     * True when the direct TCP peer is loopback or an RFC1918 private address
     * — i.e. our own container/VM network (Nginx, Docker Compose, a K8s
     * ingress, etc.), not a caller reachable directly from the internet.
     */
    private boolean isTrustedProxyAddress(String addr) {
        if (addr == null) return false;
        if (addr.equals("127.0.0.1") || addr.equals("0:0:0:0:0:0:0:1") || addr.equals("::1")) {
            return true;
        }
        return addr.startsWith("10.")
                || addr.startsWith("192.168.")
                || addr.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
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