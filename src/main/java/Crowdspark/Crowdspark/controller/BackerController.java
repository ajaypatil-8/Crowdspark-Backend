package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.BackerDashboardResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.BackerService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backer")
@RequiredArgsConstructor
public class BackerController {

    private final BackerService backerService;
    private final UserService   userService;

    /**
     * GET /api/backer/dashboard
     * Returns both stats + backed-projects list in one call.
     * Authenticated — any logged-in user can back projects.
     */
    @GetMapping("/dashboard")
    public BackerDashboardResponse getDashboard(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return backerService.getDashboard(user.getId());
    }

    /**
     * GET /api/backer/backed-projects
     * Convenience alias — returns just the backedProjects list.
     */
    @GetMapping("/backed-projects")
    public Object getBackedProjects(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return backerService.getDashboard(user.getId()).getBackedProjects();
    }

    /**
     * GET /api/backer/stats
     * Convenience alias — returns just totalProjectsBacked + totalAmountBacked.
     */
    @GetMapping("/stats")
    public Object getStats(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        BackerDashboardResponse dash = backerService.getDashboard(user.getId());
        return new java.util.LinkedHashMap<String, Object>() {{
            put("totalProjectsBacked", dash.getTotalProjectsBacked());
            put("totalAmountBacked",   dash.getTotalAmountBacked());
        }};
    }
}
