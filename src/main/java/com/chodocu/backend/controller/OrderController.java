package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.admin.FeeCalculationResponse;
import com.chodocu.backend.dto.order.OrderCreateRequest;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.OrderService;
import com.chodocu.backend.service.PlatformSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PlatformSettingService platformSettingService;

    /**
     * Xem trước phí sàn cho một giá trị đơn.
     * App hiển thị "Bạn nhận được / Bạn thanh toán" trước khi đặt mua.
     */
    @GetMapping("/fee-preview")
    public ResponseEntity<ApiResponse<FeeCalculationResponse>> previewFee(@RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok("Biểu phí sàn áp dụng", platformSettingService.calculate(amount)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Chi tiết đơn hàng",
                orderService.getOrderById(id, currentUser.getEmail())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        OrderResponse created = orderService.createOrderRequest(request, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi yêu cầu đặt mua / hẹn gặp cho người bán!", created));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String note = body != null ? body.get("sellerNote") : null;
        OrderResponse updated = orderService.acceptOrder(id, currentUser.getEmail(), note);
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt yêu cầu! Tin đăng đã chuyển sang Đang giao dịch.", updated));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> completeOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        OrderResponse updated = orderService.completeOrder(id, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Giao dịch thành công! Tin đăng đã chuyển sang Đã bán.", updated));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrCancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String reason = body != null ? body.get("reason") : null;
        OrderResponse updated = orderService.rejectOrCancelOrder(id, currentUser.getEmail(), reason);
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy / từ chối yêu cầu giao dịch.", updated));
    }

    @PutMapping("/{id}/dispute")
    public ResponseEntity<ApiResponse<OrderResponse>> disputeOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        String reason = body != null ? body.get("reason") : "Không có mô tả chi tiết";
        OrderResponse updated = orderService.disputeOrder(id, currentUser.getEmail(), reason);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi báo cáo tranh chấp tới admin.", updated));
    }

    @GetMapping("/my-purchases")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyPurchases(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Lịch sử đặt mua của bạn", orderService.getMyPurchases(currentUser.getEmail())));
    }

    @GetMapping("/my-sales")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMySales(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Yêu cầu mua & lịch sử bán của bạn", orderService.getMySales(currentUser.getEmail())));
    }
}
