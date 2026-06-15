package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import Crowdspark.Crowdspark.dto.FcmSubscribeRequest;
import Crowdspark.Crowdspark.service.PushNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications — list, mark read, delete")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService         userService;
    private final PushNotificationService pushNotificationService;

    /** GET /api/notifications?page=0&size=20 */
    @Operation(summary = "Get notifications (paginated)", security = @SecurityRequirement(name = "bearerAuth"))
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
    @Operation(summary = "Get unread count", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", count)));
    }

    /** PUT /api/notifications/{id}/read */
    @Operation(summary = "Mark notification as read", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", notificationService.markRead(id, user.getId())));
    }

    /** PUT /api/notifications/read-all */
    @Operation(summary = "Mark all as read", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        int updated = notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("markedRead", updated)));
    }

    /** DELETE /api/notifications/{id} */
    @Operation(summary = "Delete a notification", security = @SecurityRequirement(name = "bearerAuth"))
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
    @Operation(summary = "Clear all notifications", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/clear-all")
    public ResponseEntity<ApiResponse<Void>> clearAll(
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        notificationService.deleteAllNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("All notifications cleared", null));
    }


    // ── POST /api/notifications/subscribe ───────────────────────────────────

    @Operation(
            summary  = "Register FCM token",
            description = "Call after the browser grants notification permission. " +
                    "Idempotent — safe to call on every page load.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @Valid @RequestBody FcmSubscribeRequest request,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        pushNotificationService.subscribe(user.getId(), request.getToken(),
                request.getDeviceHint());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>builder()
                        .success(true).message("Push notifications enabled").build());
    }

    // ── DELETE /api/notifications/unsubscribe ────────────────────────────────

    @Operation(
            summary  = "Unregister FCM token",
            description = "Remove a specific device token (user turned off push notifications).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            @Valid @RequestBody FcmSubscribeRequest request,
            @AuthenticationPrincipal String username
    ) {
        User user = userService.getByUsername(username);
        pushNotificationService.unsubscribe(user.getId(), request.getToken());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true).message("Push notifications disabled").build());
    }
}