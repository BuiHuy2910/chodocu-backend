package com.chodocu.backend.repository;

import com.chodocu.backend.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
    Optional<PlatformSetting> findByKey(String key);
}
