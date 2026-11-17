package com.chodocu.backend.repository;

import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"university"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countGroupByRole();

    @EntityGraph(attributePaths = {"university"})
    List<User> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"university"})
    @Query("SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role) " +
            "AND (:keyword = '' OR LOWER(u.fullName) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(u.email) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(u.studentId, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(u.phone, '')) LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(
            @Param("role") Role role,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /** Xóa avatar tự sinh (dicebear / pravatar) của dữ liệu cũ -> app sẽ hiện chữ cái đầu tên. */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.avatarUrl = NULL " +
            "WHERE u.avatarUrl LIKE '%dicebear%' OR u.avatarUrl LIKE '%pravatar%'")
    int clearGeneratedAvatars();
}
