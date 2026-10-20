package com.chodocu.backend.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderCreateRequest {
    @NotNull(message = "Mã sản phẩm không được để trống")
    private Long productId;

    private BigDecimal offeredPrice;

    private String meetupLocation;

    private String meetupTime;

    private String buyerNote;
}
