// src/main/java/Crowdspark/Crowdspark/security/filter/DeprecatedApiRedirectFilter.java
// Feature #26 — API Versioning — Backward-Compatibility Filter
//
// WHY:  Old clients hitting /api/* or /admin/* would get 404s after versioning.
//       This filter rewrites those to /api/v1/* transparently.
//
// HTTP 308 (Permanent Redirect) preserves the original HTTP method — critical
// for POST /api/v1/payment/create-order etc. (301 would silently downgrade to GET).
//
// Add  app.api.deprecation.sunset-date=2026-09-16  to application.properties
// to inform clients via the Sunset header. Remove this filter after that date.

package Crowdspark.Crowdspark.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@Order(1)
public class DeprecatedApiRedirectFilter implements Filter {

    @Value("${app.api.deprecation.sunset-date:}")
    private String sunsetDate;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri  = request.getRequestURI();
        String path = (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length()) : uri;

        String redirectTarget = null;

        // /api/xxx  (but NOT /api/v1/xxx and NOT /api-docs)
        if (path.startsWith("/api/") && !path.startsWith("/api/v1/") && !path.startsWith("/api-docs")) {
            redirectTarget = contextPath + "/api/v1" + path.substring("/api".length());
        }
        // /admin/xxx  →  /api/v1/admin/xxx
        else if (path.equals("/admin") || path.startsWith("/admin/")) {
            String suffix = path.startsWith("/admin/") ? path.substring("/admin".length()) : "";
            redirectTarget = contextPath + "/api/v1/admin" + suffix;
        }

        if (redirectTarget != null) {
            String qs = request.getQueryString();
            if (qs != null && !qs.isBlank()) redirectTarget += "?" + qs;

            log.debug("Deprecated path {} → {}", uri, redirectTarget);

            String sunset = (sunsetDate != null && !sunsetDate.isBlank()) ? sunsetDate
                    : LocalDate.now().plusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE);

            response.setHeader("Deprecation", "true");
            response.setHeader("Sunset",      sunset);
            response.setHeader("Link",        "<" + redirectTarget + ">; rel=\"successor-version\"");
            response.setHeader("Warning",     "299 - \"Deprecated path. Use " + redirectTarget + "\"");
            response.setHeader("Location",    redirectTarget);
            response.setStatus(308);   // 308 Permanent Redirect — preserves HTTP method
            return;
        }

        chain.doFilter(request, response);
    }
}
