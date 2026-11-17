package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Wishlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    @EntityGraph(attributePaths = {"product", "product.category", "product.university", "product.seller", "product.seller.university"})
    List<Wishlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT w.product.id FROM Wishlist w WHERE w.user.id = :userId AND w.product.id IN :productIds")
    Set<Long> findFavoritedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    void deleteByProductId(Long productId);
}
