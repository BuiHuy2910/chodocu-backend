package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.DeviceTokenRequest;
import com.chodocu.backend.dto.NotificationResponse;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.FcmPushService;
import com.chodocu.backend.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final FcmPushService fcmPushService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.ok("Thông báo của bạn",
                notificationService.getMyNotifications(currentUser.getEmail())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        long count = notificationService.getUnreadCount(currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Số thông báo chưa đọc", Map.of("count", count)));
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAllRead(currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu tất cả là đã đọc", null));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id,
                                                      @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markRead(id, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đã đọc", null));
    }

    @PostMapping("/device-token")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @Valid @RequestBody DeviceTokenRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        fcmPushService.registerToken(currentUser.getEmail(), request);
        return ResponseEntity.ok(ApiResponse.ok("Đã đăng ký thiết bị nhận thông báo", null));
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<ApiResponse<Void>> unregisterDeviceToken(
            @RequestParam String token,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        fcmPushService.unregisterToken(currentUser.getEmail(), token);
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy đăng ký thông báo thiết bị", null));
    }
}
