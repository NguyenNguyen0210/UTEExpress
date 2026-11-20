package com.uteexpress.controller.api;

import com.uteexpress.dto.ws.NotificationListResponse;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    @GetMapping("")
    public ResponseEntity<NotificationListResponse> getRecentNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(notificationService.getRecentNotifications(currentUser.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }
}
