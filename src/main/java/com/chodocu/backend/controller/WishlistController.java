package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleWishlist(
            @PathVariable Long productId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        boolean favorited = wishlistService.toggleWishlist(productId, currentUser.getEmail());
        String msg = favorited ? "Đã lưu vào danh sách Yêu thích!" : "Đã bỏ khỏi danh sách Yêu thích";
        return ResponseEntity.ok(ApiResponse.ok(msg, Map.of("favorited", favorited)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getMyWishlist(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách tin yêu thích", wishlistService.getMyWishlist(currentUser.getEmail())));
    }
}
