package com.chodocu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bậc phí sàn. Admin cấu hình để áp dụng tỷ lệ phí khác nhau theo giá trị đơn.
 * Ví dụ: đơn < 100k phí 3%, đơn 100k-1tr phí 4%, đơn > 1tr phí 5%.
 */
@Entity
@Table(name = "platform_fee_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformFeeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 0)
    private BigDecimal maxAmount;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal feeRate;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Integer priority = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean matches(BigDecimal amount) {
        if (amount == null) return false;
        boolean lowerOk = amount.compareTo(minAmount) >= 0;
        boolean upperOk = maxAmount == null || amount.compareTo(maxAmount) <= 0;
        return lowerOk && upperOk;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
