package com.chodocu.backend.repository;

import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {

    @EntityGraph(attributePaths = {
            "buyer", "buyer.university",
            "seller", "seller.university",
            "product", "product.category", "product.university", "product.seller", "product.seller.university"
    })
    Optional<OrderRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {
            "buyer", "buyer.university",
            "seller", "seller.university",
            "product", "product.category", "product.university", "product.seller", "product.seller.university"
    })
    List<OrderRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @EntityGraph(attributePaths = {
            "buyer", "buyer.university",
            "seller", "seller.university",
            "product", "product.category", "product.university", "product.seller", "product.seller.university"
    })
    List<OrderRequest> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<OrderRequest> findByProductIdOrderByCreatedAtDesc(Long productId);

    @EntityGraph(attributePaths = {
            "buyer", "buyer.university",
            "seller", "seller.university",
            "product", "product.category", "product.university", "product.seller", "product.seller.university"
    })
    @Query("SELECT o FROM OrderRequest o JOIN o.product p JOIN o.buyer b JOIN o.seller s " +
            "WHERE (:status IS NULL OR o.status = :status) " +
            "AND (:keyword = '' OR (:exactId IS NOT NULL AND o.id = :exactId) " +
            "OR LOWER(COALESCE(o.orderCode, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(p.title) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(b.fullName) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(b.email) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(s.fullName) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(s.email) LIKE CONCAT('%', :keyword, '%'))")
    Page<OrderRequest> searchAdminOrders(
            @Param("status") OrderStatus status,
            @Param("keyword") String keyword,
            @Param("exactId") Long exactId,
            Pageable pageable
    );

    /** Các đơn khác của cùng sản phẩm đang ở 1 trạng thái (dùng để tự từ chối khi đã chốt đơn). */
    List<OrderRequest> findByProductIdAndStatusAndIdNot(Long productId, OrderStatus status, Long excludedOrderId);

    long countByStatus(OrderStatus status);

    @Query("SELECT o.status, COUNT(o), COALESCE(SUM(o.offeredPrice), 0) FROM OrderRequest o GROUP BY o.status")
    List<Object[]> getOrderStatsByStatus();

    boolean existsByProductId(Long productId);

    /** Người mua đã có đơn đang xử lý cho sản phẩm này chưa (chống đặt trùng). */
    boolean existsByBuyerIdAndProductIdAndStatusIn(Long buyerId, Long productId, Collection<OrderStatus> statuses);
}
