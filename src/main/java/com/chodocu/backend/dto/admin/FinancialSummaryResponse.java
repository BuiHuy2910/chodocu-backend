package com.chodocu.backend.dto.admin;

import com.chodocu.backend.dto.PageResponse;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSummaryResponse {
    private BigDecimal totalTransactionValue;
    private BigDecimal totalPlatformFees;
    private Long totalCompletedOrders;
    private BigDecimal currentFeeRate;
    private BigDecimal minFee;
    private BigDecimal maxFee;
    private BigDecimal roundStep;
    private String feePayer;
    private Boolean feeEnabled;
    private List<TransactionResponse> recentTransactions;
    private PageResponse<TransactionResponse> transactionsPage;
    private List<FeeTierDto> feeTiers;
    private Map<String, FeeCalculationResponse> feePreviews;
}
