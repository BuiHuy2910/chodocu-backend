package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.auth.AuthResponse;
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
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký tài khoản sinh viên thành công!", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công!", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSummaryDto>> getCurrentUser(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Chưa đăng nhập"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Thông tin tài khoản", authService.getCurrentUser(currentUser.getEmail())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody UpdateProfileRequest request
    ) {
        UserSummaryDto updated = authService.updateProfile(request, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ sinh viên thành công!", updated));
    }

    /**
     * Đổi avatar nhanh: client upload ảnh qua /api/upload rồi gửi URL vào đây.
     */
    @PutMapping("/avatar")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateAvatar(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody Map<String, String> body
    ) {
        String avatarUrl = body.get("avatarUrl");
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu đường dẫn ảnh đại diện"));
        }
        UserSummaryDto updated = authService.updateAvatar(currentUser.getEmail(), avatarUrl);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật ảnh đại diện!", updated));
    }
}
