package com.chodocu.backend.repository;

import com.chodocu.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Toàn bộ tin nhắn giữa 2 người, cũ nhất trước. */
    @Query("SELECT m FROM ChatMessage m WHERE " +
            "(m.sender.id = :user1Id AND m.receiver.id = :user2Id) OR " +
            "(m.sender.id = :user2Id AND m.receiver.id = :user1Id) " +
            "ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversation(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    /** Mọi tin nhắn liên quan tới 1 người, mới nhất trước (dùng để dựng hộp thư). */
    @Query("SELECT m FROM ChatMessage m WHERE m.sender.id = :userId OR m.receiver.id = :userId ORDER BY m.createdAt DESC")
    List<ChatMessage> findAllMessagesForUser(@Param("userId") Long userId);

    /** Tổng số tin chưa đọc mà người dùng nhận được. */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.receiver.id = :userId AND m.isRead = false")
    long countUnreadForUser(@Param("userId") Long userId);

    /** Đánh dấu đã đọc mọi tin partner gửi cho user. Trả về số tin được cập nhật. */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true " +
            "WHERE m.sender.id = :partnerId AND m.receiver.id = :userId AND m.isRead = false")
    int markConversationRead(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    /** Gỡ liên kết sản phẩm khỏi tin nhắn (trước khi xóa sản phẩm), giữ lại lịch sử chat. */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.product = NULL WHERE m.product.id = :productId")
    int clearProductReference(@Param("productId") Long productId);
}
