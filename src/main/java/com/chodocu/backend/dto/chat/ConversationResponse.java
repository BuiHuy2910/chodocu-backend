package com.chodocu.backend.dto.chat;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Một dòng trong hộp thư: mỗi người đối thoại là một hội thoại. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private UserSummaryDto partner;

    /** Sản phẩm của tin nhắn gần nhất (có thể null nếu chỉ nhắn chung). */
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal productPrice;

    /** Nội dung xem trước, ví dụ "📷 Hình ảnh" hoặc "Trả giá: 120.000 đ". */
    private String lastMessage;
    private String lastMessageType;
    /** true nếu tin cuối do chính mình gửi (app hiện "Bạn: ..."). */
    private Boolean lastMessageMine;
    private String lastMessageTime;
    private String lastMessageAtIso;

    /** Số tin người kia gửi mà mình chưa đọc. */
    private Integer unreadCount;
}
