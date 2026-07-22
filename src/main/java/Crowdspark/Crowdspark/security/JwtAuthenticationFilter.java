package Crowdspark.Crowdspark.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // no token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // invalid token
        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = jwtUtil.extractClaims(token);

        // BUG FIX (Feature #23): pending-2FA tokens (issued after password
        // check, before the TOTP code is verified) must never be usable as a
        // general-purpose Bearer token — only /auth/totp/verify-login should
        // ever accept them. Previously this wasn't checked at all here, and
        // since generatePendingTotpToken() never sets a "roles" claim, the
        // roles.stream() call below would NullPointerException on any request
        // that presented one — an uncontrolled 500 crash, not a clean
        // rejection, and not something a security filter should rely on.
        // Explicitly reject by type, and treat a missing roles claim the same
        // way as "no token" / "invalid token" rather than crashing.
        if ("pending_totp".equals(claims.get("type", String.class))) {
            filterChain.doFilter(request, response);
            return;
        }

        // 👤 userId stored in JWT subject
        String userId = claims.getSubject();

        // 🎭 roles
        List<String> roles = claims.get("roles", List.class);

        if (roles == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // convert roles to authorities
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        claims.get("username"),
                        null,
                        authorities
                );


        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}