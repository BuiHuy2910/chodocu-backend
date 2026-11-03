package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
    List<Report> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
