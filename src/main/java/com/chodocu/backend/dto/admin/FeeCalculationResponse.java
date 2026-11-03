package com.chodocu.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kết quả tính phí sàn cho một giá trị đơn hàng.
 * Dùng cho màn hình "Xem trước phí" ở admin và app mobile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCalculationResponse {
    private BigDecimal orderAmount;
    private BigDecimal feeRate;
    private String feeRatePercent;
    private BigDecimal platformFeeAmount;
    private BigDecimal sellerReceiveAmount;
    private BigDecimal buyerPayAmount;
    private String feePayer;
    private String appliedTierName;
    private boolean feeEnabled;
}
