package com.chodocu.backend.service;

import com.chodocu.backend.dto.order.OrderCreateRequest;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.entity.ChatMessage;
import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ChatMessageRepository;
import com.chodocu.backend.repository.OrderRequestRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRequestRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DtoMapper dtoMapper;

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

        OrderRequest order = OrderRequest.builder()
                .product(product)
                .buyer(buyer)
                .seller(product.getSeller())
                .offeredPrice(request.getOfferedPrice() != null ? request.getOfferedPrice() : product.getPrice())
                .meetupLocation(request.getMeetupLocation() != null && !request.getMeetupLocation().isBlank()
                        ? request.getMeetupLocation() : product.getPickupSpot())
                .meetupTime(request.getMeetupTime() != null && !request.getMeetupTime().isBlank()
                        ? request.getMeetupTime() : "17:00 Chiều nay")
                .buyerNote(request.getBuyerNote())
                .status(OrderStatus.PENDING)
                .build();

        OrderRequest savedOrder = orderRepository.save(order);

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

        return dtoMapper.toOrderResponse(savedOrder);
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

        order.setStatus(OrderStatus.ACCEPTED);
        if (sellerNote != null) order.setSellerNote(sellerNote);

        // Chuyển trạng thái sản phẩm sang Đang giao dịch (IN_TRANSACTION) để ẩn khỏi feed tìm kiếm chung
        Product product = order.getProduct();
        product.setStatus(ProductStatus.IN_TRANSACTION);
        productRepository.save(product);

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

        order.setStatus(OrderStatus.COMPLETED);

        // Chuyển trạng thái sản phẩm sang Đã bán (SOLD) để ẩn tin
        Product product = order.getProduct();
        product.setStatus(ProductStatus.SOLD);
        productRepository.save(product);

        User seller = order.getSeller();
        seller.setCompletedDeals(seller.getCompletedDeals() + 1);
        userRepository.save(seller);

        return dtoMapper.toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse rejectOrCancelOrder(Long orderId, String userEmail, String reason) {
        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đặt mua"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (order.getSeller().getId().equals(user.getId())) {
            order.setStatus(OrderStatus.REJECTED);
            order.setSellerNote(reason);
        } else if (order.getBuyer().getId().equals(user.getId())) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setBuyerNote(reason);
        } else {
            throw new BadRequestException("Bạn không có quyền thao tác trên đơn này");
        }

        // Mở lại tin đăng nếu sản phẩm đang ở trạng thái IN_TRANSACTION
        Product product = order.getProduct();
        if (product.getStatus() == ProductStatus.IN_TRANSACTION) {
            product.setStatus(ProductStatus.APPROVED);
            productRepository.save(product);
        }

        return dtoMapper.toOrderResponse(orderRepository.save(order));
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
