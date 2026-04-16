package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.BackerDashboardResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.BackerService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/backer")
@RequiredArgsConstructor
public class BackerController {

    private final BackerService backerService;
    private final UserService   userService;

    /** GET /api/backer/dashboard — stats + backed-projects in one call */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<BackerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(backerService.getDashboard(user.getId())));
    }

    /** GET /api/backer/backed-projects */
    @GetMapping("/backed-projects")
    public ResponseEntity<ApiResponse<Object>> getBackedProjects(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(
                backerService.getDashboard(user.getId()).getBackedProjects()
        ));
    }

    /** GET /api/backer/stats */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        BackerDashboardResponse dash = backerService.getDashboard(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalProjectsBacked", dash.getTotalProjectsBacked(),
                "totalAmountBacked",   dash.getTotalAmountBacked()
        )));
    }
}
