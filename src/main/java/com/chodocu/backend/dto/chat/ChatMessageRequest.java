package com.chodocu.backend.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Tin nhắn gửi lên từ app.
 * Mỗi loại tin cần dữ liệu riêng (kiểm tra ở ChatService):
 * TEXT cần content, IMAGE cần imageUrl, OFFER cần offeredPrice, MEETUP cần meetupLocation + meetupTime.
 */
@Data
public class ChatMessageRequest {

    @NotNull(message = "Người nhận không được để trống")
    private Long receiverId;

    private Long productId;

    @Size(max = 1000, message = "Tin nhắn tối đa 1000 ký tự")
    private String content;

    /** TEXT, IMAGE, OFFER, MEETUP (SYSTEM chỉ do server tạo). */
    private String messageType = "TEXT";

    @Size(max = 1000, message = "Đường dẫn ảnh quá dài")
    private String imageUrl;

    private BigDecimal offeredPrice;

    @Size(max = 255, message = "Điểm hẹn quá dài")
    private String meetupLocation;

    @Size(max = 255, message = "Thời gian hẹn quá dài")
    private String meetupTime;
}
