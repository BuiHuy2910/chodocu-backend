package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.FeeCalculationResponse;
import com.chodocu.backend.dto.order.OrderCreateRequest;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.entity.*;
import com.chodocu.backend.enums.MessageType;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.*;
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Vòng đời đơn hàng: đặt mua -> người bán chấp nhận -> gặp mặt -> hoàn tất
 * (hoặc từ chối / hủy / tranh chấp).
 * Mỗi bước đều gửi thông báo và tin nhắn hệ thống vào khung chat của 2 bên.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /** Đơn ở các trạng thái này coi như "đang xử lý" -> không cho đặt trùng. */
    private static final Set<OrderStatus> ACTIVE_STATUSES =
            Set.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.DISPUTED);

    /** Chỉ chốt giao dịch khi tin đang mở bán (hoặc người bán đang tạm ẩn tin). */
    private static final Set<ProductStatus> SELLABLE_PRODUCT_STATUSES =
            Set.of(ProductStatus.APPROVED, ProductStatus.HIDDEN);

    /** Khớp độ dài cột ghi chú / lý do trong bảng order_requests. */
    private static final int MAX_NOTE_LENGTH = 500;

    private final OrderRequestRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final DtoMapper dtoMapper;
    private final PlatformSettingService platformSettingService;
    private final NotificationService notificationService;
    private final ChatService chatService;

    // ----------------------------------------------------------------
    // Người mua đặt mua
    // ----------------------------------------------------------------

    @Transactional
    public OrderResponse createOrderRequest(OrderCreateRequest request, String buyerEmail) {
        User buyer = findUserByEmail(buyerEmail);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        validateCanOrder(product, buyer);
        // Điểm hẹn do người mua tự chọn trong danh sách của trường, giờ hẹn trong 30 ngày tới
        String meetupLocation = MeetupRules.requireSpot(product.getUniversity(), request.getMeetupLocation());
        String meetupTime = MeetupRules.requireTime(request.getMeetupTime());

        BigDecimal offeredPrice = request.getOfferedPrice() != null ? request.getOfferedPrice() : product.getPrice();

        // Chốt phí sàn NGAY khi tạo đơn để tránh admin đổi cấu hình giữa chừng
        FeeCalculationResponse fee = platformSettingService.calculate(offeredPrice);
        PlatformFeeTier tier = platformSettingService.resolveTier(offeredPrice);

        OrderRequest order = OrderRequest.builder()
                .product(product)
                .buyer(buyer)
                .seller(product.getSeller())
                .offeredPrice(offeredPrice)
                .meetupLocation(meetupLocation)
                .meetupTime(meetupTime)
                .buyerNote(cleanNote(request.getBuyerNote()))
                .status(OrderStatus.PENDING)
                .platformFeeRate(fee.getFeeRate())
                .platformFeeAmount(fee.getPlatformFeeAmount())
                .finalSellerAmount(fee.getSellerReceiveAmount())
                .finalBuyerAmount(fee.getBuyerPayAmount())
                .feePayer(fee.getFeePayer())
                .feeTierId(tier != null ? tier.getId() : null)
                .build();

        OrderRequest savedOrder = orderRepository.save(order);
        savedOrder.setOrderCode(String.format("PD%06d", savedOrder.getId()));

        sendMeetupMessage(savedOrder);
        notificationService.createNotification(
                product.getSeller(),
                "ORDER_RECEIVED",
                "Bạn có yêu cầu đặt mua mới! 🛒",
                buyer.getFullName() + " muốn mua \"" + product.getTitle() + "\" với giá "
                        + DisplayFormat.money(offeredPrice) + " (mã đơn " + savedOrder.getOrderCode() + ")",
                savedOrder.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(savedOrder));
    }

    // ----------------------------------------------------------------
    // Người bán chấp nhận / hai bên hoàn tất
    // ----------------------------------------------------------------

    @Transactional
    public OrderResponse acceptOrder(Long orderId, String sellerEmail, String sellerNote) {
        OrderRequest order = findOrder(orderId);
        User seller = findUserByEmail(sellerEmail);

        if (!order.getSeller().getId().equals(seller.getId())) {
            throw new BadRequestException("Chỉ người bán mới có thể duyệt yêu cầu này");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt đơn đang ở trạng thái Chờ duyệt");
        }
        if (!SELLABLE_PRODUCT_STATUSES.contains(order.getProduct().getStatus())) {
            throw new BadRequestException("Tin đăng đang chờ duyệt hoặc đã kết thúc, chưa thể chốt giao dịch");
        }

        order.setStatus(OrderStatus.ACCEPTED);
        order.setConfirmedAt(DisplayFormat.nowUtc());
        order.setStatusUpdatedBy(seller.getId());
        String note = cleanNote(sellerNote);
        if (note != null) {
            order.setSellerNote(note);
        }

        // Sản phẩm chuyển sang Đang giao dịch -> ẩn khỏi feed, các yêu cầu khác tự bị từ chối
        Product product = order.getProduct();
        product.setStatus(ProductStatus.IN_TRANSACTION);
        productRepository.save(product);
        rejectOtherPendingOrders(order);

        chatService.sendSystemMessage(seller, order.getBuyer(), product,
                "Người bán đã chấp nhận yêu cầu mua. Hẹn gặp tại " + order.getMeetupLocation()
                        + " lúc " + DisplayFormat.meetupTime(order.getMeetupTime()) + "."
                        + noteSuffix("Ghi chú", order.getSellerNote()));
        notificationService.createNotification(
                order.getBuyer(),
                "ORDER_ACCEPTED",
                "Người bán đã chấp nhận yêu cầu! ✅",
                seller.getFullName() + " đã chấp nhận đơn mua \"" + product.getTitle() + "\". Hãy đến gặp để hoàn tất giao dịch!",
                order.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    /** Người mua hoặc người bán xác nhận đã giao nhận xong -> chốt phí sàn, sản phẩm chuyển Đã bán. */
    @Transactional
    public OrderResponse completeOrder(Long orderId, String userEmail) {
        OrderRequest order = findOrder(orderId);
        User user = findUserByEmail(userEmail);

        if (!isParticipant(order, user)) {
            throw new BadRequestException("Bạn không có quyền cập nhật giao dịch này");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BadRequestException("Chỉ có thể hoàn tất đơn đang ở trạng thái Đang giao dịch");
        }

        // Dùng phí đã chốt lúc tạo đơn; nếu thiếu (đơn cũ) thì tính lại
        if (order.getPlatformFeeAmount() == null || order.getPlatformFeeAmount().signum() <= 0) {
            FeeCalculationResponse calc = platformSettingService.calculate(order.getOfferedPrice());
            order.setPlatformFeeRate(calc.getFeeRate());
            order.setPlatformFeeAmount(calc.getPlatformFeeAmount());
            order.setFinalSellerAmount(calc.getSellerReceiveAmount());
            order.setFinalBuyerAmount(calc.getBuyerPayAmount());
            order.setFeePayer(calc.getFeePayer());
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus("COMPLETED");
        order.setCompletedAt(DisplayFormat.nowUtc());
        order.setStatusUpdatedBy(user.getId());

        markProductSold(order);
        saveTransactionRecord(order);

        chatService.sendSystemMessage(user, otherParty(order, user), order.getProduct(),
                roleLabel(order, user) + " đã xác nhận giao dịch hoàn tất. Cảm ơn bạn đã mua bán trên Chợ Đồ Cũ!");
        notificationService.createNotification(
                order.getSeller(),
                "ORDER_COMPLETED",
                "Giao dịch hoàn thành! 💰",
                "Đơn " + order.getOrderCode() + " cho \"" + order.getProduct().getTitle()
                        + "\" đã hoàn thành. Bạn nhận: " + DisplayFormat.money(order.getFinalSellerAmount())
                        + " (phí sàn " + DisplayFormat.money(order.getPlatformFeeAmount()) + ")",
                order.getId()
        );
        notificationService.createNotification(
                order.getBuyer(),
                "ORDER_COMPLETED",
                "Giao dịch hoàn thành! ✅",
                "Bạn đã mua thành công \"" + order.getProduct().getTitle() + "\" với tổng thanh toán "
                        + DisplayFormat.money(order.getFinalBuyerAmount()),
                order.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    // ----------------------------------------------------------------
    // Tranh chấp / từ chối / hủy
    // ----------------------------------------------------------------

    @Transactional
    public OrderResponse disputeOrder(Long orderId, String userEmail, String reason) {
        OrderRequest order = findOrder(orderId);
        User user = findUserByEmail(userEmail);

        if (!isParticipant(order, user)) {
            throw new BadRequestException("Bạn không có quyền báo tranh chấp đơn này");
        }
        // Đơn chờ duyệt thì chỉ cần hủy / từ chối; tranh chấp chỉ xảy ra khi đã chốt giao dịch
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BadRequestException("Chỉ báo tranh chấp được với đơn đang giao dịch");
        }

        String cleanReason = cleanNote(reason);
        order.setStatus(OrderStatus.DISPUTED);
        order.setDisputeReason(cleanReason);
        order.setStatusUpdatedBy(user.getId());

        chatService.sendSystemMessage(user, otherParty(order, user), order.getProduct(),
                roleLabel(order, user) + " đã báo tranh chấp cho đơn " + order.getOrderCode()
                        + ". Quản trị viên sẽ xem xét và liên hệ hai bên." + noteSuffix("Lý do", cleanReason));
        String message = "Đơn " + order.getOrderCode() + " đã được đưa vào trạng thái tranh chấp và chờ admin xử lý.";
        notificationService.createNotification(order.getBuyer(), "ORDER_DISPUTED", "Đơn hàng đang tranh chấp", message, order.getId());
        notificationService.createNotification(order.getSeller(), "ORDER_DISPUTED", "Đơn hàng đang tranh chấp", message, order.getId());

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    /** Người bán từ chối (REJECTED) hoặc người mua hủy (CANCELLED) một đơn chưa kết thúc. */
    @Transactional
    public OrderResponse rejectOrCancelOrder(Long orderId, String userEmail, String reason) {
        OrderRequest order = findOrder(orderId);
        User user = findUserByEmail(userEmail);

        if (order.getStatus().isFinal()) {
            throw new BadRequestException("Đơn đã kết thúc, không thể hủy");
        }
        if (!isParticipant(order, user)) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đơn này");
        }

        OrderStatus previousStatus = order.getStatus();
        boolean isSeller = order.getSeller().getId().equals(user.getId());
        String cleanReason = cleanNote(reason);

        order.setStatus(isSeller ? OrderStatus.REJECTED : OrderStatus.CANCELLED);
        order.setCancelReason(cleanReason);
        order.setStatusUpdatedBy(user.getId());
        reopenProductIfReserved(order, previousStatus);

        String action = isSeller ? "Người bán đã từ chối yêu cầu mua." : "Người mua đã hủy yêu cầu mua.";
        chatService.sendSystemMessage(user, otherParty(order, user), order.getProduct(),
                action + noteSuffix("Lý do", cleanReason));

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    // ----------------------------------------------------------------
    // Xem đơn
    // ----------------------------------------------------------------

    public OrderResponse getOrderById(Long orderId, String userEmail) {
        OrderRequest order = findOrder(orderId);
        User user = findUserByEmail(userEmail);
        if (!isParticipant(order, user) && user.getRole() == Role.STUDENT) {
            throw new BadRequestException("Bạn không có quyền xem đơn này");
        }
        return dtoMapper.toOrderResponse(order);
    }

    public List<OrderResponse> getMyPurchases(String buyerEmail) {
        User buyer = findUserByEmail(buyerEmail);
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(dtoMapper::toOrderResponse)
                .toList();
    }

    public List<OrderResponse> getMySales(String sellerEmail) {
        User seller = findUserByEmail(sellerEmail);
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(dtoMapper::toOrderResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Admin can thiệp
    // ----------------------------------------------------------------

    /**
     * Admin can thiệp trạng thái đơn: duyệt thay, hoàn tất, hủy hoặc đánh dấu tranh chấp.
     * Dùng cho màn "Quản lý đơn hàng" ở admin.
     */
    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus target, String note, String adminEmail) {
        OrderRequest order = findOrder(orderId);
        User admin = findUserByEmail(adminEmail);

        if (target == null) {
            throw new BadRequestException("Trạng thái đơn không hợp lệ");
        }
        if (order.getStatus() == target) {
            throw new BadRequestException("Đơn đã ở trạng thái " + target);
        }

        OrderStatus previousStatus = order.getStatus();
        String adminNote = cleanNote(note);
        order.setAdminNote(adminNote);
        order.setStatusUpdatedBy(admin.getId());

        switch (target) {
            case ACCEPTED -> {
                order.setStatus(OrderStatus.ACCEPTED);
                order.setConfirmedAt(DisplayFormat.nowUtc());
                Product product = order.getProduct();
                product.setStatus(ProductStatus.IN_TRANSACTION);
                productRepository.save(product);
                rejectOtherPendingOrders(order);
            }
            case COMPLETED -> {
                if (order.getFinalSellerAmount() == null) {
                    BigDecimal fee = order.getPlatformFeeAmount() != null ? order.getPlatformFeeAmount() : BigDecimal.ZERO;
                    order.setFinalSellerAmount(order.getOfferedPrice().subtract(fee));
                }
                order.setStatus(OrderStatus.COMPLETED);
                order.setPaymentStatus("COMPLETED");
                order.setCompletedAt(DisplayFormat.nowUtc());
                markProductSold(order);
                saveTransactionRecord(order);
            }
            case DISPUTED -> {
                order.setStatus(OrderStatus.DISPUTED);
                order.setDisputeReason(adminNote);
            }
            case CANCELLED, REJECTED -> {
                order.setStatus(target);
                order.setCancelReason(adminNote != null ? adminNote : "Admin hủy đơn");
                reopenProductIfReserved(order, previousStatus);
            }
            case PENDING -> throw new BadRequestException("Không thể đưa đơn về trạng thái Chờ duyệt");
        }

        OrderRequest saved = orderRepository.save(order);
        String message = "Đơn " + saved.getOrderCode() + " chuyển sang: " + dtoMapper.getOrderStatusLabel(saved.getStatus())
                + noteSuffix("Ghi chú", adminNote);
        notificationService.createNotification(saved.getBuyer(), "ORDER_ADMIN_UPDATED",
                "Đơn hàng được cập nhật bởi admin", message, saved.getId());
        notificationService.createNotification(saved.getSeller(), "ORDER_ADMIN_UPDATED",
                "Đơn hàng được cập nhật bởi admin", message, saved.getId());

        return dtoMapper.toOrderResponse(saved);
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    private void validateCanOrder(Product product, User buyer) {
        if (product.getSeller().getId().equals(buyer.getId())) {
            throw new BadRequestException("Bạn không thể tự đặt mua sản phẩm của chính mình!");
        }
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new BadRequestException("Sản phẩm này đã được bán!");
        }
        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new BadRequestException("Sản phẩm chưa được duyệt hoặc đang giao dịch, không thể đặt mua.");
        }
        if (orderRepository.existsByBuyerIdAndProductIdAndStatusIn(buyer.getId(), product.getId(), ACTIVE_STATUSES)) {
            throw new BadRequestException("Bạn đã gửi yêu cầu mua sản phẩm này rồi");
        }
    }

    /**
     * Tin "đề xuất hẹn gặp" tự động gửi vào chat khi người mua đặt mua.
     * Thẻ hẹn gặp trong app đã hiện điểm hẹn, giờ hẹn, giá nên nội dung chỉ là lời nhắn của người mua.
     */
    private void sendMeetupMessage(OrderRequest order) {
        String content = StringUtils.hasText(order.getBuyerNote())
                ? order.getBuyerNote()
                : "Mình muốn mua món này. Hẹn gặp bạn theo lịch bên dưới nhé.";

        chatService.sendAutomaticMessage(ChatMessage.builder()
                .sender(order.getBuyer())
                .receiver(order.getSeller())
                .product(order.getProduct())
                .messageType(MessageType.MEETUP.name())
                .offeredPrice(order.getOfferedPrice())
                .meetupLocation(order.getMeetupLocation())
                .meetupTime(order.getMeetupTime())
                .content(content)
                .isRead(false)
                .build());
    }

    /** Đã chốt với 1 người mua -> tự từ chối các yêu cầu đang chờ khác của cùng sản phẩm. */
    private void rejectOtherPendingOrders(OrderRequest acceptedOrder) {
        String reason = "Người bán đã chốt giao dịch với người mua khác";
        List<OrderRequest> others = orderRepository.findByProductIdAndStatusAndIdNot(
                acceptedOrder.getProduct().getId(), OrderStatus.PENDING, acceptedOrder.getId());

        for (OrderRequest other : others) {
            other.setStatus(OrderStatus.REJECTED);
            other.setCancelReason(reason);
            other.setStatusUpdatedBy(acceptedOrder.getSeller().getId());
            orderRepository.save(other);

            chatService.sendSystemMessage(other.getSeller(), other.getBuyer(), other.getProduct(),
                    reason + ". Yêu cầu của bạn đã tự động bị từ chối.");
            notificationService.createNotification(
                    other.getBuyer(),
                    "ORDER_REJECTED",
                    "Yêu cầu mua đã bị từ chối",
                    "\"" + other.getProduct().getTitle() + "\": " + reason + ".",
                    other.getId()
            );
        }
    }

    /** Chỉ mở bán lại sản phẩm khi chính đơn này đang giữ sản phẩm (đã chấp nhận / đang tranh chấp). */
    private void reopenProductIfReserved(OrderRequest order, OrderStatus previousStatus) {
        boolean wasHoldingProduct = previousStatus == OrderStatus.ACCEPTED || previousStatus == OrderStatus.DISPUTED;
        Product product = order.getProduct();
        if (wasHoldingProduct && product.getStatus() == ProductStatus.IN_TRANSACTION) {
            product.setStatus(ProductStatus.APPROVED);
            productRepository.save(product);
        }
    }

    private void markProductSold(OrderRequest order) {
        Product product = order.getProduct();
        product.setStatus(ProductStatus.SOLD);
        productRepository.save(product);

        User seller = order.getSeller();
        seller.setCompletedDeals(seller.getCompletedDeals() + 1);
        userRepository.save(seller);
    }

    /** Lưu bản ghi giao dịch để thống kê doanh thu phí sàn (mỗi đơn chỉ 1 bản ghi). */
    private void saveTransactionRecord(OrderRequest order) {
        if (transactionRepository.existsByOrderId(order.getId())) {
            return;
        }
        transactionRepository.save(Transaction.builder()
                .order(order)
                .buyer(order.getBuyer())
                .seller(order.getSeller())
                .product(order.getProduct())
                .transactionAmount(order.getOfferedPrice())
                .platformFeeRate(order.getPlatformFeeRate() != null ? order.getPlatformFeeRate() : BigDecimal.ZERO)
                .platformFeeAmount(order.getPlatformFeeAmount() != null ? order.getPlatformFeeAmount() : BigDecimal.ZERO)
                .sellerReceiveAmount(order.getFinalSellerAmount())
                .build());
    }

    private boolean isParticipant(OrderRequest order, User user) {
        return order.getSeller().getId().equals(user.getId()) || order.getBuyer().getId().equals(user.getId());
    }

    private User otherParty(OrderRequest order, User user) {
        return order.getSeller().getId().equals(user.getId()) ? order.getBuyer() : order.getSeller();
    }

    private String roleLabel(OrderRequest order, User user) {
        return order.getSeller().getId().equals(user.getId()) ? "Người bán" : "Người mua";
    }

    /** " Lý do: ..." nếu có nội dung, ngược lại chuỗi rỗng. */
    private String noteSuffix(String label, String note) {
        return StringUtils.hasText(note) ? " " + label + ": " + note.trim() : "";
    }

    /** Ghi chú / lý do: bỏ khoảng trắng thừa, rỗng thì null, quá dài thì báo lỗi rõ ràng. */
    private String cleanNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new BadRequestException("Ghi chú / lý do tối đa " + MAX_NOTE_LENGTH + " ký tự");
        }
        return trimmed;
    }

    private OrderRequest findOrder(Long orderId) {
        return orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
