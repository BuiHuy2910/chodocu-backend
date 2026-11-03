package com.chodocu.backend.repository;

import com.chodocu.backend.entity.PlatformFeeTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformFeeTierRepository extends JpaRepository<PlatformFeeTier, Long> {
    List<PlatformFeeTier> findByActiveTrueOrderByPriorityDesc();
    List<PlatformFeeTier> findAllByOrderByPriorityDesc();
}
