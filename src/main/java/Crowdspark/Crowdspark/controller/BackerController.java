package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.BackerDashboardResponse;
import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.service.BackerService;
import Crowdspark.Crowdspark.service.RefundService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backer")
@RequiredArgsConstructor
@Tag(name = "Backer Dashboard", description = "Backer-specific views — backed projects, refunds, profile")
public class BackerController {

    private final BackerService backerService;
    private final UserService   userService;
    private final RefundService refundService;

    /** GET /api/backer/dashboard — stats + backed-projects in one call */
    @Operation(summary = "Get backer dashboard",
            description = "Returns stats and full backed-projects list in a single call.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<BackerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(backerService.getDashboard(user.getId())));
    }

    /** GET /api/backer/backed-projects */
    @Operation(summary = "Get projects I have backed", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/backed-projects")
    public ResponseEntity<ApiResponse<Object>> getBackedProjects(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                backerService.getDashboard(user.getId()).getBackedProjects()
        ));
    }

    @Operation(summary = "Get backer stats",
            description = "Returns totalBacked, totalAmountBacked, and activeCampaigns counts.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        BackerDashboardResponse dash = backerService.getDashboard(user.getId());

        long activeCampaigns = dash.getBackedProjects().stream()
                .filter(p -> ProjectStatus.APPROVED.name().equals(p.getStatus()))
                .count();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalBacked",       dash.getTotalProjectsBacked(),
                "totalAmountBacked", dash.getTotalAmountBacked(),
                "activeCampaigns",   activeCampaigns
        )));
    }

    @Operation(summary = "Get my refund history", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getMyRefunds(
            @AuthenticationPrincipal String username) {
        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.getRefundsForBacker(backer.getId())));
    }
}