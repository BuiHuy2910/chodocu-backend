package com.chodocu.backend.service;

import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.Category;
import com.chodocu.backend.entity.ChatMessage;
import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.University;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.util.DisplayFormat;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chuyển Entity (dữ liệu DB) sang DTO (dữ liệu trả cho app / trang quản trị).
 * Không bịa dữ liệu: thiếu thông tin thì trả null để app tự hiển thị phù hợp.
 *
 * Quyền riêng tư:
 * - {@link #toUserSummary} — hồ sơ đầy đủ, chỉ dùng cho chính chủ và trang quản trị.
 * - {@link #toPublicUser} — thông tin công khai (không có email, số điện thoại) cho người khác xem.
 * - Số điện thoại chỉ hiện trong đơn khi hai bên đã chốt hẹn (xem {@link #PHONE_VISIBLE}).
 */
@Component
public class DtoMapper {

    /** Trạng thái đơn mà hai bên được thấy số điện thoại của nhau. */
    private static final Set<OrderStatus> PHONE_VISIBLE =
            EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.COMPLETED, OrderStatus.DISPUTED);

    // ---------------------------------------------------------------------
    // Người dùng
    // ---------------------------------------------------------------------

    /** Hồ sơ đầy đủ: dùng cho chính chủ (đăng nhập, hồ sơ) và trang quản trị. */
    public UserSummaryDto toUserSummary(User user) {
        if (user == null) return null;
        University university = user.getUniversity();
        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .studentId(user.getStudentId())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .universityId(university != null ? university.getId() : null)
                .universityName(university != null ? university.getName() : null)
                .universityShortName(university != null ? university.getShortName() : null)
                .dormArea(user.getDormArea())
                .role(user.getRole().name())
                .bio(user.getBio())
                .active(user.getActive())
                .rating(user.getRating())
                .completedDeals(user.getCompletedDeals())
                .createdAt(DisplayFormat.dateTime(user.getCreatedAt()))
                .build();
    }

    /** Thông tin công khai của một người (người bán, người chat cùng...): không có email, số điện thoại. */
    public UserSummaryDto toPublicUser(User user) {
        if (user == null) return null;
        University university = user.getUniversity();
        return UserSummaryDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .universityId(university != null ? university.getId() : null)
                .universityName(university != null ? university.getName() : null)
                .universityShortName(university != null ? university.getShortName() : null)
                .role(user.getRole().name())
                .bio(user.getBio())
                .rating(user.getRating())
                .completedDeals(user.getCompletedDeals())
                .build();
    }

    // ---------------------------------------------------------------------
    // Tin đăng
    // ---------------------------------------------------------------------

    /** Tin đăng cho app: người bán chỉ có thông tin công khai. */
    public ProductResponse toProductResponse(Product p, boolean isFavorited) {
        return buildProduct(p, isFavorited, toPublicUser(p == null ? null : p.getSeller()));
    }

    /** Tin đăng cho trang quản trị: kèm email, số điện thoại người bán để liên hệ khi cần. */
    public ProductResponse toAdminProductResponse(Product p) {
        return buildProduct(p, false, toUserSummary(p == null ? null : p.getSeller()));
    }

    private ProductResponse buildProduct(Product p, boolean isFavorited, UserSummaryDto seller) {
        if (p == null) return null;
        Category category = p.getCategory();
        University university = p.getUniversity();
        return ProductResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .isFree(p.getIsFree())
                .isUrgent(p.getIsUrgent())
                .condition(p.getItemCondition() != null ? p.getItemCondition().name() : null)
                .conditionLabel(p.getItemCondition() != null ? p.getItemCondition().getLabel() : null)
                .conditionNote(p.getConditionNote())
                .passReason(p.getPassReason())
                .pickupSpot(p.getPickupSpot())
                .status(p.getStatus().name())
                .rejectReason(p.getRejectReason())
                .viewCount(p.getViewCount())
                .favoriteCount(p.getFavoriteCount())
                .isFavorited(isFavorited)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryIcon(category != null ? category.getIcon() : null)
                .universityId(university != null ? university.getId() : null)
                .universityName(university != null ? university.getName() : null)
                .universityShortName(university != null ? university.getShortName() : null)
                .seller(seller)
                .imageUrls(p.getImageUrls() != null ? new ArrayList<>(p.getImageUrls()) : new ArrayList<>())
                .createdAt(DisplayFormat.dateTime(p.getCreatedAt()))
                .createdAtIso(DisplayFormat.iso(p.getCreatedAt()))
                .timeAgo(formatTimeAgo(p.getCreatedAt()))
                .build();
    }

    // ---------------------------------------------------------------------
    // Đơn đặt mua
    // ---------------------------------------------------------------------

    /**
     * Đơn cho app: hai bên chỉ thấy thông tin công khai của nhau,
     * số điện thoại chỉ có khi đơn đã chốt hẹn / hoàn tất / đang khiếu nại.
     */
    public OrderResponse toOrderResponse(OrderRequest order) {
        if (order == null) return null;
        boolean showPhone = PHONE_VISIBLE.contains(order.getStatus());
        return buildOrder(order,
                partyOf(order.getBuyer(), showPhone),
                partyOf(order.getSeller(), showPhone),
                toProductResponse(order.getProduct(), false));
    }

    /** Đơn cho trang quản trị: đầy đủ thông tin hai bên để xử lý khiếu nại. */
    public OrderResponse toAdminOrderResponse(OrderRequest order) {
        if (order == null) return null;
        return buildOrder(order,
                toUserSummary(order.getBuyer()),
                toUserSummary(order.getSeller()),
                toAdminProductResponse(order.getProduct()));
    }

    private UserSummaryDto partyOf(User user, boolean showPhone) {
        UserSummaryDto dto = toPublicUser(user);
        if (dto != null && showPhone) dto.setPhone(user.getPhone());
        return dto;
    }

    private OrderResponse buildOrder(OrderRequest order, UserSummaryDto buyer, UserSummaryDto seller,
                                     ProductResponse product) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .product(product)
                .buyer(buyer)
                .seller(seller)
                .offeredPrice(order.getOfferedPrice())
                .meetupLocation(order.getMeetupLocation())
                .meetupTime(order.getMeetupTime())
                .buyerNote(order.getBuyerNote())
                .sellerNote(order.getSellerNote())
                .status(order.getStatus().name())
                .statusLabel(getOrderStatusLabel(order.getStatus()))
                .paymentStatus(order.getPaymentStatus())
                .feePayer(order.getFeePayer())
                .platformFeeRate(order.getPlatformFeeRate())
                .platformFeeAmount(order.getPlatformFeeAmount())
                .finalSellerAmount(order.getFinalSellerAmount())
                .finalBuyerAmount(order.getFinalBuyerAmount())
                .cancelReason(order.getCancelReason())
                .disputeReason(order.getDisputeReason())
                .createdAt(DisplayFormat.dateTime(order.getCreatedAt()))
                .updatedAt(DisplayFormat.dateTime(order.getUpdatedAt()))
                .completedAt(DisplayFormat.dateTime(order.getCompletedAt()))
                .createdAtIso(DisplayFormat.iso(order.getCreatedAt()))
                .updatedAtIso(DisplayFormat.iso(order.getUpdatedAt()))
                .completedAtIso(DisplayFormat.iso(order.getCompletedAt()))
                .build();
    }

    // ---------------------------------------------------------------------
    // Tin nhắn
    // ---------------------------------------------------------------------

    public ChatMessageResponse toChatMessageResponse(ChatMessage msg) {
        if (msg == null) return null;
        Product product = msg.getProduct();
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(toPublicUser(msg.getSender()))
                .receiver(toPublicUser(msg.getReceiver()))
                .productId(product != null ? product.getId() : null)
                .productTitle(product != null ? product.getTitle() : null)
                .productImage(firstImageOf(product))
                .productPrice(product != null ? product.getPrice() : null)
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .imageUrl(msg.getImageUrl())
                .offeredPrice(msg.getOfferedPrice())
                .meetupLocation(msg.getMeetupLocation())
                .meetupTime(msg.getMeetupTime())
                .isRead(msg.getIsRead())
                .createdAt(DisplayFormat.shortTime(msg.getCreatedAt()))
                .sentAt(DisplayFormat.iso(msg.getCreatedAt()))
                .build();
    }

    /** Ảnh đầu tiên của sản phẩm (dùng làm ảnh đại diện trong chat). */
    public String firstImageOf(Product product) {
        if (product == null || product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
            return null;
        }
        return product.getImageUrls().get(0);
    }

    public String getOrderStatusLabel(OrderStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Chờ người bán xác nhận";
            case ACCEPTED -> "Đã chốt hẹn";
            case COMPLETED -> "Đã hoàn tất";
            case DISPUTED -> "Đang khiếu nại";
            case REJECTED -> "Người bán đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    /** "Vừa xong", "5 phút trước"... (cả hai mốc đều là giờ UTC). */
    private String formatTimeAgo(LocalDateTime utcTime) {
        if (utcTime == null) return "";
        Duration duration = Duration.between(utcTime, DisplayFormat.nowUtc());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        long hours = duration.toHours();
        if (hours < 24) return hours + " giờ trước";
        return duration.toDays() + " ngày trước";
    }
}
