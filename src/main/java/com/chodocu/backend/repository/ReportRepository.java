package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query(value = "SELECT r.id, rep.fullName, p.title, r.reason, r.detail, r.status, r.adminNote, r.createdAt " +
            "FROM Report r JOIN r.reporter rep LEFT JOIN r.product p " +
            "WHERE (:status = 'ALL' OR r.status = :status) " +
            "AND (:keyword = '' OR LOWER(rep.fullName) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(p.title, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(r.reason) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(r.detail, '')) LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Report r JOIN r.reporter rep LEFT JOIN r.product p " +
                    "WHERE (:status = 'ALL' OR r.status = :status) " +
                    "AND (:keyword = '' OR LOWER(rep.fullName) LIKE CONCAT('%', :keyword, '%') " +
                    "OR LOWER(COALESCE(p.title, '')) LIKE CONCAT('%', :keyword, '%') " +
                    "OR LOWER(r.reason) LIKE CONCAT('%', :keyword, '%') " +
                    "OR LOWER(COALESCE(r.detail, '')) LIKE CONCAT('%', :keyword, '%'))")
    Page<Object[]> searchAdminReportRows(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    long countByStatus(String status);

    /** Gỡ liên kết sản phẩm khỏi báo cáo (trước khi xóa sản phẩm), giữ lại nội dung báo cáo. */
    @Modifying
    @Query("UPDATE Report r SET r.product = NULL WHERE r.product.id = :productId")
    int clearProductReference(@Param("productId") Long productId);
}
