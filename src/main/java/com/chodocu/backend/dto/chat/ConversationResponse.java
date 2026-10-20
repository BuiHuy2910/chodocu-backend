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
public class ConversationResponse {
    private UserSummaryDto partner;
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal productPrice;
    private String lastMessage;
    private String lastMessageTime;
    private Integer unreadCount;
}
