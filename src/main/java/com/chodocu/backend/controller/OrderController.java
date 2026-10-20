package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.order.OrderCreateRequest;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
