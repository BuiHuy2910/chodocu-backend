package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.product.ProductRequest;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.enums.ProductCondition;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) ProductCondition condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean urgentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        PageResponse<ProductResponse> result = productService.searchProducts(
                keyword, categoryId, universityId, condition, minPrice, maxPrice, urgentOnly,
                page, size, sortBy, sortDir, userId
        );
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tin đăng", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.ok("Chi tiết sản phẩm", productService.getProductById(id, userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestParam(defaultValue = "true") boolean autoApprove,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        ProductResponse created = productService.createProduct(request, currentUser.getEmail(), autoApprove);
        return ResponseEntity.ok(ApiResponse.ok("Đăng tin thành công!", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        ProductResponse updated = productService.updateProduct(id, request, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tin đăng thành công!", updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam ProductStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        ProductResponse updated = productService.updateProductStatus(id, status, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái tin đăng!", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        productService.deleteProduct(id, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa tin đăng thành công!", null));
    }

    @GetMapping("/my-posts")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Tin đã đăng của bạn", productService.getMyPostedProducts(currentUser.getEmail())));
    }
}
