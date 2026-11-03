package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.ReportResponse;
import com.chodocu.backend.dto.admin.*;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;
    private final PlatformSettingService platformSettingService;
    private final ReportService reportService;
    private final OrderService orderService;

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
    public ResponseEntity<ApiResponse<ProductResponse>> approveProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt tin đăng thành công!", adminService.approveProduct(id, currentUser.getEmail())));
    }

    @PutMapping("/products/{id}/reject")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối tin đăng!", adminService.rejectProduct(id, reason, currentUser.getEmail())));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        productService.deleteProduct(id, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa tin đăng khỏi hệ thống!", null));
    }

    /** Ẩn / hiện lại tin đăng mà không xóa dữ liệu. */
    @PutMapping("/products/{id}/hide")
    public ResponseEntity<ApiResponse<ProductResponse>> hideProduct(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean hidden,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                hidden ? "Đã ẩn tin đăng khỏi sàn!" : "Đã hiển thị lại tin đăng!",
                adminService.hideProduct(id, hidden, currentUser.getEmail())));
    }

    /** Nhật ký kiểm duyệt gần nhất (ai duyệt, lý do gì). */
    @GetMapping("/moderation-logs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getModerationLogs() {
        return ResponseEntity.ok(ApiResponse.ok("Nhật ký kiểm duyệt", adminService.getModerationLogs()));
    }

    // --- Orders (quản lý toàn bộ đơn hàng) ---
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách đơn hàng", adminService.getAllOrders(status)));
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(body.getOrDefault("status", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Trạng thái đơn không hợp lệ"));
        }
        OrderResponse updated = orderService.adminUpdateStatus(id, target, body.get("note"), currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái đơn hàng!", updated));
    }

    // --- Fee tiers (bậc phí sàn) ---
    @GetMapping("/fee-tiers")
    public ResponseEntity<ApiResponse<List<FeeTierDto>>> getFeeTiers() {
        return ResponseEntity.ok(ApiResponse.ok("Bậc phí sàn", platformSettingService.getAllFeeTiers()));
    }

    @PostMapping("/fee-tiers")
    public ResponseEntity<ApiResponse<FeeTierDto>> createFeeTier(@RequestBody FeeTierDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm bậc phí!", platformSettingService.saveFeeTier(null, dto)));
    }

    @PutMapping("/fee-tiers/{id}")
    public ResponseEntity<ApiResponse<FeeTierDto>> updateFeeTier(@PathVariable Long id, @RequestBody FeeTierDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật bậc phí!", platformSettingService.saveFeeTier(id, dto)));
    }

    @DeleteMapping("/fee-tiers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFeeTier(@PathVariable Long id) {
        platformSettingService.deleteFeeTier(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã tắt bậc phí!", null));
    }

    /** Xem trước phí sàn — dùng cho bảng mô phỏng ở admin. */
    @GetMapping("/fee/preview")
    public ResponseEntity<ApiResponse<FeeCalculationResponse>> previewFee(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Xem trước phí sàn", platformSettingService.calculate(amount)));
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

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserSummaryDto>> changeUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        UserSummaryDto updated = adminService.changeUserRole(id, body.get("role"));
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật phân quyền!", updated));
    }

    // --- Platform Settings / Fee Config ---
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<PlatformSettingDto>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok("Cài đặt hệ thống", platformSettingService.getAllSettings()));
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<PlatformSettingDto>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật cài đặt!",
                platformSettingService.updateSetting(key, body.get("value"), currentUser.getEmail())));
    }

    @GetMapping("/financial")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> getFinancial() {
        return ResponseEntity.ok(ApiResponse.ok("Tổng quan tài chính", platformSettingService.getFinancialSummary()));
    }

    // --- Reports ---
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getReports(
            @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách báo cáo vi phạm", reportService.getAllReports(status)));
    }

    @PutMapping("/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportResponse>> resolveReport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String action = body.getOrDefault("action", "RESOLVED");
        String note = body.getOrDefault("note", "");
        return ResponseEntity.ok(ApiResponse.ok("Đã xử lý báo cáo!",
                reportService.resolveReport(id, note, action, currentUser.getEmail())));
    }
}
