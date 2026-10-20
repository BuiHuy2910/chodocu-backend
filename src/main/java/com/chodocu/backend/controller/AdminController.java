package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.admin.DashboardStatsResponse;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.AdminService;
import com.chodocu.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok("Thống kê hệ thống", adminService.getDashboardStats()));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) ProductStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tin đăng quản trị", adminService.getAllProductsForAdmin(status)));
    }

    @PutMapping("/products/{id}/approve")
    public ResponseEntity<ApiResponse<ProductResponse>> approveProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt tin đăng thành công!", adminService.approveProduct(id)));
    }

    @PutMapping("/products/{id}/reject")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối tin đăng!", adminService.rejectProduct(id, reason)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        productService.deleteProduct(id, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa tin đăng khỏi hệ thống!", null));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tài khoản sinh viên", adminService.getAllUsers()));
    }

    @PutMapping("/users/{id}/toggle-active")
    public ResponseEntity<ApiResponse<UserSummaryDto>> toggleUserActive(@PathVariable Long id) {
        UserSummaryDto updated = adminService.toggleUserActive(id);
        String msg = Boolean.TRUE.equals(updated.getActive()) ? "Đã mở khóa tài khoản!" : "Đã khóa tài khoản!";
        return ResponseEntity.ok(ApiResponse.ok(msg, updated));
    }

    @PutMapping("/users/{id}/toggle-verified")
    public ResponseEntity<ApiResponse<UserSummaryDto>> toggleUserVerified(@PathVariable Long id) {
        UserSummaryDto updated = adminService.toggleUserVerified(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái xác thực sinh viên!", updated));
    }
}
