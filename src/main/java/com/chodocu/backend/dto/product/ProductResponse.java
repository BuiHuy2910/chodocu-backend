package com.chodocu.backend.dto.product;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Boolean isFree;
    private Boolean isUrgent;
    private String condition;
    private String conditionLabel;
    private String conditionNote;
    private String passReason;
    private String pickupSpot;
    private String status;
    private String rejectReason;
    private Integer viewCount;
    private Integer favoriteCount;
    private Boolean isFavorited;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private Long universityId;
    private String universityName;
    private String universityShortName;
    private UserSummaryDto seller;
    private List<String> imageUrls;
    private String createdAt;
    private String timeAgo;
}
