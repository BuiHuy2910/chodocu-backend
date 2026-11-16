package com.chodocu.backend.repository;

import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    List<OrderRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<OrderRequest> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<OrderRequest> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<OrderRequest> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    /** Các đơn khác của cùng sản phẩm đang ở 1 trạng thái (dùng để tự từ chối khi đã chốt đơn). */
    List<OrderRequest> findByProductIdAndStatusAndIdNot(Long productId, OrderStatus status, Long excludedOrderId);

    long countByStatus(OrderStatus status);

    boolean existsByProductId(Long productId);

    /** Người mua đã có đơn đang xử lý cho sản phẩm này chưa (chống đặt trùng). */
    boolean existsByBuyerIdAndProductIdAndStatusIn(Long buyerId, Long productId, Collection<OrderStatus> statuses);
}
