package com.chodocu.backend.dto.product;

import com.chodocu.backend.enums.ProductCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {
    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    private BigDecimal price;

    private BigDecimal originalPrice;

    private Boolean isFree = false;

    private Boolean isUrgent = false;

    private ProductCondition condition = ProductCondition.LIKE_NEW_95;

    private String conditionNote;

    private String passReason;

    private String pickupSpot;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotNull(message = "Trường đại học / Khu vực không được để trống")
    private Long universityId;

    private List<String> imageUrls;
}
