package com.chodocu.backend.repository;

import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    List<User> findAllByOrderByCreatedAtDesc();

    /** Xóa avatar tự sinh (dicebear / pravatar) của dữ liệu cũ -> app sẽ hiện chữ cái đầu tên. */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.avatarUrl = NULL " +
            "WHERE u.avatarUrl LIKE '%dicebear%' OR u.avatarUrl LIKE '%pravatar%'")
    int clearGeneratedAvatars();
}
