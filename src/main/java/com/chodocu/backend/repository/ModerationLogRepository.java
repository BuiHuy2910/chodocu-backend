package com.chodocu.backend.repository;

import com.chodocu.backend.entity.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {
    List<ModerationLog> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<ModerationLog> findTop100ByOrderByCreatedAtDesc();
}
