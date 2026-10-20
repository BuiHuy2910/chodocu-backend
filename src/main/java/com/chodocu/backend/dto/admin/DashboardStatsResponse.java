package com.chodocu.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalStudents;
    private long totalProducts;
    private long pendingProducts;
    private long approvedProducts;
    private long inTransactionProducts;
    private long soldProducts;
    private long totalOrders;
    private long completedOrders;
    private BigDecimal totalTransactionValue;
    private Map<String, Long> productsByCategory;
    private Map<String, Long> productsByUniversity;
}
