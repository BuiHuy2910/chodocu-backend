package com.chodocu.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChatMessageRequest {
    @NotNull(message = "Người nhận không được để trống")
    private Long receiverId;

    private Long productId;

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String content;

    private String messageType = "TEXT"; // TEXT, OFFER, MEETUP

    private BigDecimal offeredPrice;

    private String meetupLocation;

    private String meetupTime;
}
