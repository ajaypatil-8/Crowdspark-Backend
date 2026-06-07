// src/main/java/Crowdspark/Crowdspark/controller/AnalyticsController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ProjectAnalyticsResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.AnalyticsService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Tag(name = "Analytics", description = "Project view tracking and creator analytics dashboard")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService      userService;

    /**
     * POST /api/projects/{id}/view
     *
     * Called by the frontend on every project detail page load.
     * Tracks the view (async — non-blocking) and counts unique visitors
     * using a SHA-256 hash of IP + User-Agent (no PII stored).
     *
     * Public — no auth required. Anonymous views are tracked too.
     */
    @Operation(summary = "Track a project page view",
               description = "Called once per page load. Async — returns immediately.")
    @PostMapping("/{projectId}/view")
    public ResponseEntity<Void> trackView(
            @PathVariable Long projectId,
            HttpServletRequest request) {

        String visitorKey = hashVisitor(extractIp(request),
                request.getHeader("User-Agent"));
        analyticsService.recordView(projectId, visitorKey);
        return ResponseEntity.accepted().build();   // 202 — fire and forget
    }

    /**
     * GET /api/projects/{id}/analytics
     *
     * Returns full analytics for a project.
     * Only the project creator may access their own analytics.
     */
    @Operation(summary = "Get project analytics (creator only)",
               description = "Returns 30-day views, funding progress, conversion rate, engagement.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/{projectId}/analytics")
    public ResponseEntity<ApiResponse<ProjectAnalyticsResponse>> getAnalytics(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        ProjectAnalyticsResponse analytics =
                analyticsService.getAnalytics(projectId, creator.getId());
        return ResponseEntity.ok(ApiResponse.ok(analytics));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** SHA-256(ip + userAgent) — privacy-safe unique visitor fingerprint */
    private String hashVisitor(String ip, String userAgent) {
        try {
            String raw = ip + "|" + (userAgent != null ? userAgent : "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // first 16 hex chars
        } catch (Exception e) {
            return ip; // fallback
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return request.getRemoteAddr();
    }
}
