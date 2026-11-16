package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    /** Gỡ liên kết sản phẩm khỏi báo cáo (trước khi xóa sản phẩm), giữ lại nội dung báo cáo. */
    @Modifying
    @Query("UPDATE Report r SET r.product = NULL WHERE r.product.id = :productId")
    int clearProductReference(@Param("productId") Long productId);
}
