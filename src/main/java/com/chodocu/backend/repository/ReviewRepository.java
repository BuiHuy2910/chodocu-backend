package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByReviewedUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Review> findByOrderIdAndReviewerId(Long orderId, Long reviewerId);

    @Query("SELECT COALESCE(AVG(r.rating), 5.0) FROM Review r WHERE r.reviewedUser.id = :userId")
    Double averageRatingByReviewedUserId(Long userId);
}
