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
    private String createdAt;
    private String updatedAt;
}
