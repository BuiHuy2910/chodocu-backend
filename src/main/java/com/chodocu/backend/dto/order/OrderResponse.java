package com.chodocu.backend.dto.order;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.product.ProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderCode;
    private ProductResponse product;
    private UserSummaryDto buyer;
    private UserSummaryDto seller;
    private BigDecimal offeredPrice;
    private String meetupLocation;
    private String meetupTime;
    private String buyerNote;
    private String sellerNote;
    private String status;
    private String statusLabel;
    private String paymentStatus;
    private String feePayer;
    private BigDecimal platformFeeRate;
    private BigDecimal platformFeeAmount;
    private BigDecimal finalSellerAmount;
    private BigDecimal finalBuyerAmount;
    private String cancelReason;
    private String disputeReason;
    private String createdAt;
    private String updatedAt;
    private String completedAt;
}
