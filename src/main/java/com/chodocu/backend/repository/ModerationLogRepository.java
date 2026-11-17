package com.chodocu.backend.repository;

import com.chodocu.backend.entity.ModerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    @Query("SELECT l FROM ModerationLog l LEFT JOIN FETCH l.admin WHERE l.product.id = :productId ORDER BY l.createdAt DESC")
    List<ModerationLog> findByProductIdWithAdmin(@Param("productId") Long productId);

    @Query("SELECT l.product.id, l.id, l.action, l.reason, a.fullName, l.fromStatus, l.toStatus, l.createdAt " +
            "FROM ModerationLog l LEFT JOIN l.admin a " +
            "WHERE l.product.id IN :productIds ORDER BY l.createdAt DESC")
    List<Object[]> findRowsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query(value = "SELECT l.id, p.id, p.title, a.fullName, l.action, l.fromStatus, l.toStatus, l.reason, l.createdAt " +
            "FROM ModerationLog l LEFT JOIN l.product p LEFT JOIN l.admin a ORDER BY l.createdAt DESC",
            countQuery = "SELECT COUNT(l) FROM ModerationLog l")
    Page<Object[]> findAdminLogRows(Pageable pageable);

    void deleteByProductId(Long productId);
}
