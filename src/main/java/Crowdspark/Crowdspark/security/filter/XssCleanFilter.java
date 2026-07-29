// src/main/java/Crowdspark/Crowdspark/security/filter/XssCleanFilter.java
package Crowdspark.Crowdspark.security.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies XSS sanitization to every incoming request.
 * Runs FIRST in the filter chain (ORDER = HIGHEST_PRECEDENCE)
 * so the cleaned request is what Spring Security and controllers see.
 *
 * Skipped for:
 *   - Multipart requests (file uploads — binary data, not text)
 *   - OPTIONS preflight requests
 *   - AUDIT FIX (Feature #4/#14): the Razorpay webhook. Its signature is an
 *     HMAC over the EXACT bytes Razorpay sent (see
 *     PaymentServiceImpl.verifyWebhookSignature) — parsing and re-serializing
 *     the body here (even if nothing gets rewritten) can change whitespace
 *     and field ordering, which would break that signature check for every
 *     single webhook call. Raw-body-signed endpoints must never pass through
 *     a body-rewriting filter.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class XssCleanFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private static final String RAZORPAY_WEBHOOK_PATH = "/api/v1/payment/webhook";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Skip multipart (file uploads), OPTIONS, and the raw-body-signed webhook
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null
                && contentType.toLowerCase().contains("multipart");
        boolean isOptions = "OPTIONS".equalsIgnoreCase(request.getMethod());
        boolean isRawBodySignedWebhook = RAZORPAY_WEBHOOK_PATH.equals(request.getServletPath());

        if (isMultipart || isOptions || isRawBodySignedWebhook) {
            chain.doFilter(request, response);
            return;
        }

        try {
            XssRequestWrapper wrapped = new XssRequestWrapper(request, objectMapper);
            chain.doFilter(wrapped, response);
        } catch (Exception e) {
            log.error("XssCleanFilter error: {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }
}
