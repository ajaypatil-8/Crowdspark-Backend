

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.BackerDashboardResponse;
import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.service.BackerService;
import Crowdspark.Crowdspark.service.RefundService;
import Crowdspark.Crowdspark.service.UserService;
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
public class BackerController {

    private final BackerService backerService;
    private final UserService   userService;
    private final RefundService refundService;

    /** GET /api/backer/dashboard — stats + backed-projects in one call */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<BackerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(backerService.getDashboard(user.getId())));
    }

    /** GET /api/backer/backed-projects */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/backed-projects")
    public ResponseEntity<ApiResponse<Object>> getBackedProjects(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                backerService.getDashboard(user.getId()).getBackedProjects()
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @AuthenticationPrincipal String username) {
        User user = userService.getByUsername(username);
        BackerDashboardResponse dash = backerService.getDashboard(user.getId());

        // Count active campaigns (status = APPROVED) from the backed projects list
        long activeCampaigns = dash.getBackedProjects().stream()
                .filter(p -> ProjectStatus.APPROVED.name().equals(p.getStatus()))
                .count();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                // FIX: key renamed to "totalBacked" to match frontend BackerStatsResponse
                "totalBacked",       dash.getTotalProjectsBacked(),
                "totalAmountBacked", dash.getTotalAmountBacked(),
                // FIX: added missing "activeCampaigns" field
                "activeCampaigns",   activeCampaigns
        )));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getMyRefunds(
            @AuthenticationPrincipal String username) {
        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.getRefundsForBacker(backer.getId())));
    }
}