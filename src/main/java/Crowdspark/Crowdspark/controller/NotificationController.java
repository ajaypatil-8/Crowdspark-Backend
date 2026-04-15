package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService         userService;

    /**
     * GET /api/notifications?page=0&size=20
     * Returns paginated notifications for logged-in user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User user = userService.getByUsername(username);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationResponse> result = notificationService.getNotifications(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/notifications/unread-count
     * Returns number of unread notifications — used by notification bell badge.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", count)));
    }

    /**
     * PUT /api/notifications/{id}/read
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        NotificationResponse response = notificationService.markRead(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", response));
    }

    /**
     * PUT /api/notifications/read-all
     * Mark ALL notifications as read.
     */
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        int updated = notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("markedRead", updated)));
    }
}
