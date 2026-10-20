package com.chodocu.backend.service;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.ChatMessage;
import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Component
public class DtoMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public UserSummaryDto toUserSummary(User user) {
        if (user == null) return null;
        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .studentId(user.getStudentId())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .universityId(user.getUniversity() != null ? user.getUniversity().getId() : null)
                .universityName(user.getUniversity() != null ? user.getUniversity().getName() : "Đại học Quốc Gia")
                .universityShortName(user.getUniversity() != null ? user.getUniversity().getShortName() : "ĐHQG")
                .dormArea(user.getDormArea())
                .role(user.getRole().name())
                .verifiedStudent(user.getVerifiedStudent())
                .active(user.getActive())
                .rating(user.getRating())
                .completedDeals(user.getCompletedDeals())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : "")
                .build();
    }

    public ProductResponse toProductResponse(Product p, boolean isFavorited) {
        if (p == null) return null;
        return ProductResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .isFree(p.getIsFree())
                .isUrgent(p.getIsUrgent())
                .condition(p.getItemCondition() != null ? p.getItemCondition().name() : "LIKE_NEW_95")
                .conditionLabel(p.getItemCondition() != null ? p.getItemCondition().getLabel() : "Như mới (95%)")
                .conditionNote(p.getConditionNote())
                .passReason(p.getPassReason())
                .pickupSpot(p.getPickupSpot())
                .status(p.getStatus().name())
                .rejectReason(p.getRejectReason())
                .viewCount(p.getViewCount())
                .favoriteCount(p.getFavoriteCount())
                .isFavorited(isFavorited)
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "Khác")
                .categoryIcon(p.getCategory() != null ? p.getCategory().getIcon() : "inventory_2")
                .universityId(p.getUniversity() != null ? p.getUniversity().getId() : null)
                .universityName(p.getUniversity() != null ? p.getUniversity().getName() : "")
                .universityShortName(p.getUniversity() != null ? p.getUniversity().getShortName() : "")
                .seller(toUserSummary(p.getSeller()))
                .imageUrls(p.getImageUrls() != null ? p.getImageUrls() : Collections.emptyList())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().format(FORMATTER) : "")
                .timeAgo(formatTimeAgo(p.getCreatedAt()))
                .build();
    }

    public OrderResponse toOrderResponse(OrderRequest order) {
        if (order == null) return null;
        return OrderResponse.builder()
                .id(order.getId())
                .product(toProductResponse(order.getProduct(), false))
                .buyer(toUserSummary(order.getBuyer()))
                .seller(toUserSummary(order.getSeller()))
                .offeredPrice(order.getOfferedPrice())
                .meetupLocation(order.getMeetupLocation())
                .meetupTime(order.getMeetupTime())
                .buyerNote(order.getBuyerNote())
                .sellerNote(order.getSellerNote())
                .status(order.getStatus().name())
                .statusLabel(getOrderStatusLabel(order.getStatus()))
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().format(FORMATTER) : "")
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().format(FORMATTER) : "")
                .build();
    }

    public ChatMessageResponse toChatMessageResponse(ChatMessage msg) {
        if (msg == null) return null;
        Product prod = msg.getProduct();
        String firstImg = (prod != null && prod.getImageUrls() != null && !prod.getImageUrls().isEmpty())
                ? prod.getImageUrls().get(0) : null;
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(toUserSummary(msg.getSender()))
                .receiver(toUserSummary(msg.getReceiver()))
                .productId(prod != null ? prod.getId() : null)
                .productTitle(prod != null ? prod.getTitle() : null)
                .productImage(firstImg)
                .productPrice(prod != null ? prod.getPrice() : null)
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .offeredPrice(msg.getOfferedPrice())
                .meetupLocation(msg.getMeetupLocation())
                .meetupTime(msg.getMeetupTime())
                .isRead(msg.getIsRead())
                .createdAt(msg.getCreatedAt() != null ? msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM")) : "")
                .build();
    }

    private String getOrderStatusLabel(OrderStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Chờ người bán duyệt";
            case ACCEPTED -> "Đang giao dịch / Hẹn gặp";
            case COMPLETED -> "Đã hoàn tất giao dịch";
            case REJECTED -> "Người bán đã từ chối";
            case CANCELLED -> "Đã hủy yêu cầu";
        };
    }

    private String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "Vừa xong";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        long hours = duration.toHours();
        if (hours < 24) return hours + " giờ trước";
        long days = duration.toDays();
        return days + " ngày trước";
    }
}
