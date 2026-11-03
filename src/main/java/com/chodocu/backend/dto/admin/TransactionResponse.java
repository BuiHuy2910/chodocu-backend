package com.chodocu.backend.dto.admin;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private Long id;
    private Long orderId;
    private String productTitle;
    private String buyerName;
    private String sellerName;
    private BigDecimal transactionAmount;
    private BigDecimal platformFeeRate;
    private BigDecimal platformFeeAmount;
    private BigDecimal sellerReceiveAmount;
    private String createdAt;
}
