package com.chodocu.backend.entity;

import com.chodocu.backend.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** Mã đơn hiển thị cho người dùng, ví dụ PD000123 */
    @Column(length = 20, unique = true)
    private String orderCode;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal offeredPrice;

    private String meetupLocation;

    private String meetupTime;

    @Column(length = 500)
    private String buyerNote;

    @Column(length = 500)
    private String sellerNote;

    // Platform fee fields
    @Column(precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal platformFeeRate = BigDecimal.ZERO;

    @Column(precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal platformFeeAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 0)
    private BigDecimal finalSellerAmount;

    /** Số tiền người mua phải trả (giá + phí nếu người mua chịu phí) */
    @Column(precision = 15, scale = 0)
    private BigDecimal finalBuyerAmount;

    /** SELLER | BUYER | SPLIT */
    @Column(length = 20)
    @Builder.Default
    private String feePayer = "SELLER";

    private Long feeTierId;

    // PENDING_PAYMENT, COMPLETED, WAIVED
    @Builder.Default
    private String paymentStatus = "PENDING_PAYMENT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    private LocalDateTime confirmedAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String cancelReason;

    @Column(length = 500)
    private String disputeReason;

    @Column(length = 500)
    private String adminNote;

    private Long statusUpdatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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
