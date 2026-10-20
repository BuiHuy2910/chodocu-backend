package com.chodocu.backend.dto.chat;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private UserSummaryDto sender;
    private UserSummaryDto receiver;
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal productPrice;
    private String content;
    private String messageType;
    private BigDecimal offeredPrice;
    private String meetupLocation;
    private String meetupTime;
    private Boolean isRead;
    private String createdAt;
}
