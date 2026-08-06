// src/main/java/Crowdspark/Crowdspark/logging/MdcUserIdInterceptor.java
// Feature #32 — Structured JSON logging: MDC request tracing (half 2 of 2)
//
// HandlerInterceptor.preHandle() always runs after the ENTIRE servlet filter
// chain — Spring Security included — and before the controller method is
// invoked. That's a guaranteed ordering built into Spring MVC itself, unlike
// trying to stack another @Order'd Filter and hoping it lands after Spring
// Security's own chain (which is exactly the class of bug already found and
// fixed in DeprecatedApiRedirectFilter for Feature #26 — not repeating that
// mistake here).
//
// userId here is the authenticated USERNAME, not the numeric DB id. See
// JwtAuthenticationFilter: it stores claims.get("username") — not the JWT's
// numeric subject claim — as the Authentication principal, so that's what's
// actually available. Usernames are unique and, unlike a raw numeric id,
// immediately meaningful when scanning logs during an incident.

package Crowdspark.Crowdspark.logging;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class MdcUserIdInterceptor implements HandlerInterceptor {

    public static final String USER_ID_MDC_KEY = "userId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                              @NonNull HttpServletResponse response,
                              @NonNull Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // "anonymousUser" is Spring Security's own principal for unauthenticated
        // requests when anonymous authentication is enabled — auth.isAuthenticated()
        // is true for it too (a well-known Spring Security quirk), so checking
        // isAuthenticated() alone would still tag every public GET with a
        // meaningless "userId". Excluding by principal name is what actually works.
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            MDC.put(USER_ID_MDC_KEY, auth.getName());
        }
        return true;
    }
}
