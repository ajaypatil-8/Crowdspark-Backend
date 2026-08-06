// src/main/java/Crowdspark/Crowdspark/logging/RequestTracingFilter.java
// Feature #32 — Structured JSON logging: MDC request tracing (half 1 of 2)
//
// Populates traceId + endpoint into MDC for every log line emitted while a
// request is being processed, and echoes traceId back as a response header
// so a client or support ticket can reference the exact request later.
//
// userId is deliberately NOT set here — this filter runs before Spring
// Security's authentication (see @Order below, matching the reasoning
// already established for RateLimitFilter/DeprecatedApiRedirectFilter: those
// run at HIGHEST_PRECEDENCE / +1 / +2 specifically so they execute before
// Spring Security's chain), so the authenticated principal isn't known yet
// at this point. See MdcUserIdInterceptor for that other half.

package Crowdspark.Crowdspark.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)   // after Xss/RateLimit/DeprecatedRedirect, still well before Spring Security
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String ENDPOINT_MDC_KEY = "endpoint";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Respect an upstream-assigned trace ID (load balancer / API gateway
        // / reverse proxy) if one is already present, otherwise mint a new one.
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            MDC.put(ENDPOINT_MDC_KEY, request.getMethod() + " " + request.getRequestURI());
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            // Clears traceId, endpoint, AND userId (added later by
            // MdcUserIdInterceptor, on this same thread) in one place, once
            // the entire request -- Security and the controller included --
            // has finished. Without this, MDC entries leak onto whatever
            // request this thread happens to handle next from the pool.
            MDC.clear();
        }
    }
}
