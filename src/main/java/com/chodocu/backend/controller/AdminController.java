package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.ReportResponse;
import com.chodocu.backend.dto.admin.*;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API trang quản trị (đã hỗ trợ phân trang và gom truy vấn tối ưu cho Supabase).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private final AdminService adminService;
    private final ProductService productService;
    private final PlatformSettingService platformSettingService;
    private final ReportService reportService;
    private final OrderService orderService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        return ResponseEntity.ok(ApiResponse.ok("Thống kê hệ thống", adminService.getDashboardStats(isAdmin)));
    }

    // --- Kiểm duyệt & Sản phẩm ---

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String school,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tin đăng quản trị",
                adminService.getProductsPage(status, keyword, category, school, page, size, sortBy, sortDir)));
    }

    /** Hàng chờ duyệt gom trọn ngữ cảnh (phân trang + thống kê người bán + lịch sử) trong 1 request. */
    @GetMapping("/products/pending-queue")
    public ResponseEntity<ApiResponse<PendingQueueResponse>> getPendingQueue(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String school,
            @RequestParam(defaultValue = "oldest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Hàng chờ kiểm duyệt",
                adminService.getPendingQueue(keyword, category, school, sort, page, size)));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Chi tiết tin đăng", adminService.getProductForAdmin(id)));
    }

    /** Lịch sử kiểm duyệt của 1 tin, mới nhất trước. */
    @GetMapping("/products/{id}/logs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProductLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lịch sử kiểm duyệt", adminService.getProductLogs(id)));
    }

    @PutMapping("/products/{id}/approve")
    public ResponseEntity<ApiResponse<ProductResponse>> approveProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt tin đăng thành công!", adminService.approveProduct(id, currentUser.getEmail())));
    }

    /** Body: {"reason": "..."} — lý do là bắt buộc. */
    @PutMapping("/products/{id}/reject")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối tin đăng!", adminService.rejectProduct(id, reason, currentUser.getEmail())));
    }

    /** Duyệt nhiều tin. Trả về {processed, skipped}. */
    @PutMapping("/products/bulk-approve")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkApprove(
            @Valid @RequestBody BulkModerationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Map<String, Integer> result = adminService.bulkApprove(request.getIds(), currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt " + result.get("processed") + " tin đăng", result));
    }

    /** Từ chối nhiều tin với cùng một lý do. Trả về {processed, skipped}. */
    @PutMapping("/products/bulk-reject")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkReject(
            @Valid @RequestBody BulkModerationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Map<String, Integer> result = adminService.bulkReject(request.getIds(), request.getReason(), currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối " + result.get("processed") + " tin đăng", result));
    }

    @PreAuthorize(ADMIN_ONLY)
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

    /** Nhật ký kiểm duyệt toàn hệ thống (phân trang). */
    @GetMapping("/moderation-logs")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> getModerationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Nhật ký kiểm duyệt", adminService.getModerationLogsPage(page, size)));
    }

    // --- Đơn hàng (chỉ ADMIN) ---

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách đơn hàng",
                adminService.getOrdersPage(status, keyword, page, size)));
    }

    /** Body: {"status": "CANCELLED", "note": "..."} */
    @PreAuthorize(ADMIN_ONLY)
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        OrderResponse updated = orderService.adminUpdateStatus(
                id, parseOrderStatus(body.get("status")), body.get("note"), currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái đơn hàng!", updated));
    }

    // --- Bậc phí sàn & Tài chính (chỉ ADMIN) ---

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/fee-tiers")
    public ResponseEntity<ApiResponse<List<FeeTierDto>>> getFeeTiers() {
        return ResponseEntity.ok(ApiResponse.ok("Bậc phí sàn", platformSettingService.getAllFeeTiers()));
    }

    @PreAuthorize(ADMIN_ONLY)
    @PostMapping("/fee-tiers")
    public ResponseEntity<ApiResponse<FeeTierDto>> createFeeTier(@RequestBody FeeTierDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm bậc phí!", platformSettingService.saveFeeTier(null, dto)));
    }

    @PreAuthorize(ADMIN_ONLY)
    @PutMapping("/fee-tiers/{id}")
    public ResponseEntity<ApiResponse<FeeTierDto>> updateFeeTier(@PathVariable Long id, @RequestBody FeeTierDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật bậc phí!", platformSettingService.saveFeeTier(id, dto)));
    }

    @PreAuthorize(ADMIN_ONLY)
    @DeleteMapping("/fee-tiers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFeeTier(@PathVariable Long id) {
        platformSettingService.deleteFeeTier(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã tắt bậc phí!", null));
    }

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/fee/preview")
    public ResponseEntity<ApiResponse<FeeCalculationResponse>> previewFee(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Xem trước phí sàn", platformSettingService.calculate(amount)));
    }

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/financial")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> getFinancial(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Tổng quan tài chính", platformSettingService.getFinancialSummary(page, size)));
    }

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách giao dịch", platformSettingService.getTransactionsPage(page, size)));
    }

    // --- Người dùng (chỉ ADMIN) ---

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryDto>>> getAllUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tài khoản",
                adminService.getUsersPage(role, keyword, page, size)));
    }

    @PreAuthorize(ADMIN_ONLY)
    @PutMapping("/users/{id}/toggle-active")
    public ResponseEntity<ApiResponse<UserSummaryDto>> toggleUserActive(@PathVariable Long id) {
        UserSummaryDto updated = adminService.toggleUserActive(id);
        String msg = Boolean.TRUE.equals(updated.getActive()) ? "Đã mở khóa tài khoản!" : "Đã khóa tài khoản!";
        return ResponseEntity.ok(ApiResponse.ok(msg, updated));
    }

    @PreAuthorize(ADMIN_ONLY)
    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserSummaryDto>> changeUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        UserSummaryDto updated = adminService.changeUserRole(id, body.get("role"));
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật phân quyền!", updated));
    }

    // --- Cài đặt hệ thống (chỉ ADMIN) ---

    @PreAuthorize(ADMIN_ONLY)
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<PlatformSettingDto>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok("Cài đặt hệ thống", platformSettingService.getAllSettings()));
    }

    @PreAuthorize(ADMIN_ONLY)
    @PutMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<PlatformSettingDto>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật cài đặt!",
                platformSettingService.updateSetting(key, body.get("value"), currentUser.getEmail())));
    }

    // --- Báo cáo vi phạm ---

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> getReports(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách báo cáo vi phạm",
                reportService.getReportsPage(status, keyword, page, size)));
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

    private OrderStatus parseOrderStatus(String raw) {
        try {
            return OrderStatus.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái đơn không hợp lệ");
        }
    }
}
