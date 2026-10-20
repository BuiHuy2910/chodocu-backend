package com.chodocu.backend.repository;

import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    List<OrderRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<OrderRequest> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    List<OrderRequest> findByProductIdOrderByCreatedAtDesc(Long productId);
    long countByStatus(OrderStatus status);
}
