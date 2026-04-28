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

    /** GET /api/notifications?page=0&size=20 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User user = userService.getByUsername(username);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotifications(user.getId(), pageable)));
    }

    /** GET /api/notifications/unread-count — used by notification bell badge */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", count)));
    }

    /** PUT /api/notifications/{id}/read */
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", notificationService.markRead(id, user.getId())));
    }

    /** PUT /api/notifications/read-all */
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        int updated = notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("markedRead", updated)));
    }

    /** DELETE /api/notifications/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOne(
            @PathVariable Long id,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        notificationService.deleteNotification(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted", null));
    }

    /** DELETE /api/notifications/clear-all */
    @DeleteMapping("/clear-all")
    public ResponseEntity<ApiResponse<Void>> clearAll(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        notificationService.deleteAllNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("All notifications cleared", null));
    }
}
