package com.chodocu.backend.repository;

import com.chodocu.backend.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    @Query("SELECT d.token FROM DeviceToken d WHERE d.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.user.id = :userId AND d.token = :token")
    void deleteByUserIdAndToken(@Param("userId") Long userId, @Param("token") String token);

    @Transactional
    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.token IN :tokens")
    void deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
