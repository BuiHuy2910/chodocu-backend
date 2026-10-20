package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Product;
import com.chodocu.backend.enums.ProductCondition;
import com.chodocu.backend.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' " +
            "AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:universityId IS NULL OR p.university.id = :universityId) " +
            "AND (:condition IS NULL OR p.itemCondition = :condition) " +
            "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
            "AND (:urgentOnly IS NULL OR :urgentOnly = false OR p.isUrgent = true)")
    Page<Product> searchApprovedProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("universityId") Long universityId,
            @Param("condition") ProductCondition condition,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("urgentOnly") Boolean urgentOnly,
            Pageable pageable
    );

    List<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);

    List<Product> findAllByOrderByCreatedAtDesc();

    long countByStatus(ProductStatus status);
}
