package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.auth.AuthResponse;
import com.chodocu.backend.dto.auth.LoginRequest;
import com.chodocu.backend.dto.auth.RegisterRequest;
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
            @RequestBody Map<String, Object> body
    ) {
        String fullName = (String) body.get("fullName");
        String phone = (String) body.get("phone");
        String dormArea = (String) body.get("dormArea");
        Long universityId = body.get("universityId") != null ? Long.valueOf(body.get("universityId").toString()) : null;

        UserSummaryDto updated = authService.updateProfile(currentUser.getEmail(), fullName, phone, dormArea, universityId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ sinh viên thành công!", updated));
    }
}
