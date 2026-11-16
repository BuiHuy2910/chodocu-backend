package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.auth.AuthResponse;
import com.chodocu.backend.dto.auth.ChangePasswordRequest;
import com.chodocu.backend.dto.auth.LoginRequest;
import com.chodocu.backend.dto.auth.RegisterRequest;
import com.chodocu.backend.dto.auth.UpdateProfileRequest;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký tài khoản thành công!", authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công!", authService.login(request)));
    }

    /** Thông tin tài khoản đang đăng nhập (app gọi khi mở lại để khôi phục phiên). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSummaryDto>> getCurrentUser(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.ok("Thông tin tài khoản", authService.getCurrentUser(currentUser.getEmail())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserSummaryDto updated = authService.updateProfile(request, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ thành công!", updated));
    }

    /**
     * Đổi ảnh đại diện: app upload ảnh qua /api/upload/avatar rồi gửi URL vào đây.
     * Gửi "avatarUrl" rỗng để gỡ ảnh đại diện.
     */
    @PutMapping("/avatar")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateAvatar(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody Map<String, String> body
    ) {
        UserSummaryDto updated = authService.updateAvatar(currentUser.getEmail(), body.get("avatarUrl"));
        String message = updated.getAvatarUrl() == null ? "Đã gỡ ảnh đại diện" : "Đã cập nhật ảnh đại diện!";
        return ResponseEntity.ok(ApiResponse.ok(message, updated));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUser.getEmail(), request);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công!", null));
    }
}
