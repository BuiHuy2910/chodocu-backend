package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.FeeCalculationResponse;
import com.chodocu.backend.dto.order.OrderCreateRequest;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.entity.*;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRequestRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TransactionRepository transactionRepository;
    private final DtoMapper dtoMapper;
    private final PlatformSettingService platformSettingService;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse createOrderRequest(OrderCreateRequest request, String buyerEmail) {
        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người mua"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        if (product.getSeller().getId().equals(buyer.getId())) {
            throw new BadRequestException("Bạn không thể tự đặt mua sản phẩm của chính mình!");
        }

        if (product.getStatus() == ProductStatus.SOLD) {
            throw new BadRequestException("Sản phẩm này đã được bán!");
        }
        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new BadRequestException("Sản phẩm chưa được duyệt hoặc đang giao dịch, không thể đặt mua.");
        }

        BigDecimal offeredPrice = request.getOfferedPrice() != null
                ? request.getOfferedPrice() : product.getPrice();

        // Chốt phí sàn NGAY khi tạo đơn để tránh admin đổi cấu hình giữa chừng
        FeeCalculationResponse fee = platformSettingService.calculate(offeredPrice);
        PlatformFeeTier tier = platformSettingService.resolveTier(offeredPrice);

        OrderRequest order = OrderRequest.builder()
                .product(product)
                .buyer(buyer)
                .seller(product.getSeller())
                .offeredPrice(offeredPrice)
                .meetupLocation(request.getMeetupLocation() != null && !request.getMeetupLocation().isBlank()
                        ? request.getMeetupLocation() : product.getPickupSpot())
                .meetupTime(request.getMeetupTime() != null && !request.getMeetupTime().isBlank()
                        ? request.getMeetupTime() : "17:00 Chiều nay")
                .buyerNote(request.getBuyerNote())
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

        // Tự động gửi tin nhắn hẹn gặp/đặt mua vào hộp thoại Chat giữa 2 sinh viên
        ChatMessage autoMsg = ChatMessage.builder()
                .sender(buyer)
                .receiver(product.getSeller())
                .product(product)
                .messageType("MEETUP")
                .offeredPrice(savedOrder.getOfferedPrice())
                .meetupLocation(savedOrder.getMeetupLocation())
                .meetupTime(savedOrder.getMeetupTime())
                .content("Mình gửi yêu cầu đặt mua & hẹn gặp xem [" + product.getTitle() + "] tại "
                        + savedOrder.getMeetupLocation() + " lúc " + savedOrder.getMeetupTime()
                        + (request.getBuyerNote() != null && !request.getBuyerNote().isBlank() ? " (" + request.getBuyerNote() + ")" : ""))
                .isRead(false)
                .build();
        chatMessageRepository.save(autoMsg);

        // Thông báo cho người bán
        notificationService.createNotification(
                product.getSeller(),
                "ORDER_RECEIVED",
                "Bạn có yêu cầu đặt mua mới! 🛒",
                buyer.getFullName() + " muốn mua \"" + product.getTitle() + "\" với giá "
                        + savedOrder.getOfferedPrice().toPlainString() + "đ (mã đơn " + savedOrder.getOrderCode() + ")",
                savedOrder.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(savedOrder));
    }

    @Transactional
    public OrderResponse acceptOrder(Long orderId, String sellerEmail, String sellerNote) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đặt mua"));
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người bán"));

        if (!order.getSeller().getId().equals(seller.getId())) {
            throw new BadRequestException("Chỉ người bán mới có thể duyệt yêu cầu này");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt đơn đang ở trạng thái Chờ duyệt");
        }

        order.setStatus(OrderStatus.ACCEPTED);
        order.setConfirmedAt(LocalDateTime.now());
        order.setStatusUpdatedBy(seller.getId());
        if (sellerNote != null) order.setSellerNote(sellerNote);

        // Chuyển trạng thái sản phẩm sang Đang giao dịch (IN_TRANSACTION) để ẩn khỏi feed tìm kiếm chung
        Product product = order.getProduct();
        product.setStatus(ProductStatus.IN_TRANSACTION);
        productRepository.save(product);

        // Thông báo cho người mua
        notificationService.createNotification(
                order.getBuyer(),
                "ORDER_ACCEPTED",
                "Người bán đã chấp nhận yêu cầu! ✅",
                seller.getFullName() + " đã chấp nhận đơn mua \"" + product.getTitle() + "\". Hãy đến gặp để hoàn tất giao dịch!",
                order.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId, String userEmail) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!order.getSeller().getId().equals(user.getId()) && !order.getBuyer().getId().equals(user.getId())) {
            throw new BadRequestException("Bạn không có quyền cập nhật giao dịch này");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BadRequestException("Chỉ có thể hoàn tất đơn đang ở trạng thái Đang giao dịch");
        }

        // Dùng phí đã chốt lúc tạo đơn; nếu thiếu (đơn cũ) thì tính lại
        BigDecimal fee;
        BigDecimal feeRate;
        BigDecimal sellerAmount;
        BigDecimal buyerAmount;

        if (order.getPlatformFeeAmount() != null && order.getPlatformFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            fee = order.getPlatformFeeAmount();
            feeRate = order.getPlatformFeeRate();
            sellerAmount = order.getFinalSellerAmount();
            buyerAmount = order.getFinalBuyerAmount();
        } else {
            FeeCalculationResponse calc = platformSettingService.calculate(order.getOfferedPrice());
            fee = calc.getPlatformFeeAmount();
            feeRate = calc.getFeeRate();
            sellerAmount = calc.getSellerReceiveAmount();
            buyerAmount = calc.getBuyerPayAmount();
            order.setFeePayer(calc.getFeePayer());
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setPlatformFeeRate(feeRate);
        order.setPlatformFeeAmount(fee);
        order.setFinalSellerAmount(sellerAmount);
        order.setFinalBuyerAmount(buyerAmount);
        order.setPaymentStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        order.setStatusUpdatedBy(user.getId());

        // Chuyển trạng thái sản phẩm sang Đã bán (SOLD)
        Product product = order.getProduct();
        product.setStatus(ProductStatus.SOLD);
        productRepository.save(product);

        // Cập nhật seller stats
        User seller = order.getSeller();
        seller.setCompletedDeals(seller.getCompletedDeals() + 1);
        userRepository.save(seller);

        // Tạo transaction record
        transactionRepository.save(Transaction.builder()
                .order(order)
                .buyer(order.getBuyer())
                .seller(order.getSeller())
                .product(order.getProduct())
                .transactionAmount(order.getOfferedPrice())
                .platformFeeRate(feeRate)
                .platformFeeAmount(fee)
                .sellerReceiveAmount(sellerAmount)
                .build());

        // Thông báo
        notificationService.createNotification(
                order.getSeller(),
                "ORDER_COMPLETED",
                "Giao dịch hoàn thành! 💰",
                "Đơn " + order.getOrderCode() + " cho \"" + order.getProduct().getTitle()
                        + "\" đã hoàn thành. Bạn nhận: " + sellerAmount.toPlainString() + "đ (phí sàn " + fee.toPlainString() + "đ)",
                order.getId()
        );
        notificationService.createNotification(
                order.getBuyer(),
                "ORDER_COMPLETED",
                "Giao dịch hoàn thành! ✅",
                "Bạn đã mua thành công \"" + order.getProduct().getTitle() + "\" với tổng thanh toán "
                        + buyerAmount.toPlainString() + "đ",
                order.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse disputeOrder(Long orderId, String userEmail, String reason) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!order.getSeller().getId().equals(user.getId()) && !order.getBuyer().getId().equals(user.getId())) {
            throw new BadRequestException("Bạn không có quyền báo tranh chấp đơn này");
        }
        if (order.getStatus().isFinal()) {
            throw new BadRequestException("Đơn đã kết thúc, không thể báo tranh chấp");
        }

        order.setStatus(OrderStatus.DISPUTED);
        order.setDisputeReason(reason);
        order.setStatusUpdatedBy(user.getId());

        notificationService.createNotification(
                order.getBuyer(),
                "ORDER_DISPUTED",
                "Đơn hàng đang tranh chấp",
                "Đơn " + order.getOrderCode() + " đã được đưa vào trạng thái tranh chấp và chờ admin xử lý.",
                order.getId()
        );
        notificationService.createNotification(
                order.getSeller(),
                "ORDER_DISPUTED",
                "Đơn hàng đang tranh chấp",
                "Đơn " + order.getOrderCode() + " đã được đưa vào trạng thái tranh chấp và chờ admin xử lý.",
                order.getId()
        );

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse rejectOrCancelOrder(Long orderId, String userEmail, String reason) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đặt mua"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus().isFinal()) {
            throw new BadRequestException("Đơn đã kết thúc, không thể hủy");
        }

        if (order.getSeller().getId().equals(user.getId())) {
            order.setStatus(OrderStatus.REJECTED);
            order.setSellerNote(reason);
        } else if (order.getBuyer().getId().equals(user.getId())) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setBuyerNote(reason);
        } else {
            throw new BadRequestException("Bạn không có quyền thao tác trên đơn này");
        }
        order.setCancelReason(reason);
        order.setStatusUpdatedBy(user.getId());

        // Mở lại tin đăng nếu sản phẩm đang ở trạng thái IN_TRANSACTION
        Product product = order.getProduct();
        if (product.getStatus() == ProductStatus.IN_TRANSACTION) {
            product.setStatus(ProductStatus.APPROVED);
            productRepository.save(product);
        }

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    public OrderResponse getOrderById(Long orderId, String userEmail) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (!order.getSeller().getId().equals(user.getId())
                && !order.getBuyer().getId().equals(user.getId())
                && user.getRole() == com.chodocu.backend.enums.Role.STUDENT) {
            throw new BadRequestException("Bạn không có quyền xem đơn này");
        }
        return dtoMapper.toOrderResponse(order);
    }

    /**
     * Admin can thiệp trạng thái đơn: duyệt thay, hoàn tất, hủy hoặc đánh dấu tranh chấp.
     * Dùng cho màn "Quản lý đơn hàng" ở admin.
     */
    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus target, String note, String adminEmail) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy admin"));

        if (target == null) {
            throw new BadRequestException("Trạng thái đơn không hợp lệ");
        }
        if (order.getStatus() == target) {
            throw new BadRequestException("Đơn đã ở trạng thái " + target);
        }

        order.setAdminNote(note);
        order.setStatusUpdatedBy(admin.getId());

        switch (target) {
            case ACCEPTED -> {
                order.setStatus(OrderStatus.ACCEPTED);
                order.setConfirmedAt(LocalDateTime.now());
                Product p = order.getProduct();
                p.setStatus(ProductStatus.IN_TRANSACTION);
                productRepository.save(p);
            }
            case COMPLETED -> {
                BigDecimal sellerAmount = order.getFinalSellerAmount() != null
                        ? order.getFinalSellerAmount()
                        : order.getOfferedPrice().subtract(order.getPlatformFeeAmount() == null
                        ? BigDecimal.ZERO : order.getPlatformFeeAmount());

                order.setStatus(OrderStatus.COMPLETED);
                order.setPaymentStatus("COMPLETED");
                order.setCompletedAt(LocalDateTime.now());
                order.setFinalSellerAmount(sellerAmount);

                Product p = order.getProduct();
                p.setStatus(ProductStatus.SOLD);
                productRepository.save(p);

                User seller = order.getSeller();
                seller.setCompletedDeals(seller.getCompletedDeals() + 1);
                userRepository.save(seller);

                if (!transactionRepository.existsByOrderId(order.getId())) {
                    transactionRepository.save(Transaction.builder()
                            .order(order)
                            .buyer(order.getBuyer())
                            .seller(order.getSeller())
                            .product(order.getProduct())
                            .transactionAmount(order.getOfferedPrice())
                            .platformFeeRate(order.getPlatformFeeRate() != null ? order.getPlatformFeeRate() : BigDecimal.ZERO)
                            .platformFeeAmount(order.getPlatformFeeAmount() != null ? order.getPlatformFeeAmount() : BigDecimal.ZERO)
                            .sellerReceiveAmount(sellerAmount)
                            .build());
                }
            }
            case DISPUTED -> {
                order.setStatus(OrderStatus.DISPUTED);
                order.setDisputeReason(note);
            }
            case CANCELLED, REJECTED -> {
                order.setStatus(target);
                order.setCancelReason(note != null ? note : "Admin hủy đơn");
                Product p = order.getProduct();
                if (p.getStatus() == ProductStatus.IN_TRANSACTION) {
                    p.setStatus(ProductStatus.APPROVED);
                    productRepository.save(p);
                }
            }
            case PENDING -> throw new BadRequestException("Không thể đưa đơn về trạng thái Chờ duyệt");
        }

        OrderRequest saved = orderRepository.save(order);

        String label = dtoMapper.toOrderResponse(saved).getStatusLabel();
        notificationService.createNotification(
                saved.getBuyer(),
                "ORDER_ADMIN_UPDATED",
                "Đơn hàng được cập nhật bởi admin",
                "Đơn " + saved.getOrderCode() + " chuyển sang: " + label
                        + (note != null && !note.isBlank() ? " (Ghi chú: " + note + ")" : ""),
                saved.getId()
        );
        notificationService.createNotification(
                saved.getSeller(),
                "ORDER_ADMIN_UPDATED",
                "Đơn hàng được cập nhật bởi admin",
                "Đơn " + saved.getOrderCode() + " chuyển sang: " + label
                        + (note != null && !note.isBlank() ? " (Ghi chú: " + note + ")" : ""),
                saved.getId()
        );

        return dtoMapper.toOrderResponse(saved);
    }

    public List<OrderResponse> getMyPurchases(String buyerEmail) {
        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(dtoMapper::toOrderResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getMySales(String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(dtoMapper::toOrderResponse)
                .collect(Collectors.toList());
    }
}
