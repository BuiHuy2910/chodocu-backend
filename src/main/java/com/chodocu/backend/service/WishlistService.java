package com.chodocu.backend.service;

import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.entity.Wishlist;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DtoMapper dtoMapper;

    @Transactional
    public boolean toggleWishlist(Long productId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        Optional<Wishlist> existing = wishlistRepository.findByUserIdAndProductId(user.getId(), productId);
        if (existing.isPresent()) {
            wishlistRepository.delete(existing.get());
            product.setFavoriteCount(Math.max(0, product.getFavoriteCount() - 1));
            productRepository.save(product);
            return false;
        } else {
            Wishlist wishlist = Wishlist.builder()
                    .user(user)
                    .product(product)
                    .build();
            wishlistRepository.save(wishlist);
            product.setFavoriteCount(product.getFavoriteCount() + 1);
            productRepository.save(product);
            return true;
        }
    }

    public List<ProductResponse> getMyWishlist(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> dtoMapper.toProductResponse(w.getProduct(), true))
                .collect(Collectors.toList());
    }
}
