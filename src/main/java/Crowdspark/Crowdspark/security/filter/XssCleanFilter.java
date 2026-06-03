// src/main/java/Crowdspark/Crowdspark/security/filter/XssCleanFilter.java
package Crowdspark.Crowdspark.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XssCleanFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Skip multipart (file uploads) and OPTIONS
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null
                && contentType.toLowerCase().contains("multipart");
        boolean isOptions = "OPTIONS".equalsIgnoreCase(request.getMethod());

        if (isMultipart || isOptions) {
            chain.doFilter(request, response);
            return;
        }

        try {
            XssRequestWrapper wrapped = new XssRequestWrapper(request);
            chain.doFilter(wrapped, response);
        } catch (Exception e) {
            log.error("XssCleanFilter error: {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }
}
