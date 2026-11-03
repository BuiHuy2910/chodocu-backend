package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.DashboardStatsResponse;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.order.OrderResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.ModerationLog;
import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ModerationLogRepository;
import com.chodocu.backend.repository.OrderRequestRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRequestRepository orderRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final DtoMapper dtoMapper;
    private final NotificationService notificationService;

    public DashboardStatsResponse getDashboardStats() {
        List<Product> allProducts = productRepository.findAll();
        List<OrderRequest> allOrders = orderRepository.findAll();

        BigDecimal totalValue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.ACCEPTED)
                .map(OrderRequest::getOfferedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byCategory = allProducts.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getCategory().getName(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> byUniversity = allProducts.stream()
                .filter(p -> p.getUniversity() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getUniversity().getShortName(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        return DashboardStatsResponse.builder()
                .totalStudents(userRepository.countByRole(Role.STUDENT))
                .totalModerators(userRepository.countByRole(Role.MODERATOR))
                .totalProducts(allProducts.size())
                .pendingProducts(productRepository.countByStatus(ProductStatus.PENDING))
                .approvedProducts(productRepository.countByStatus(ProductStatus.APPROVED))
                .inTransactionProducts(productRepository.countByStatus(ProductStatus.IN_TRANSACTION))
                .soldProducts(productRepository.countByStatus(ProductStatus.SOLD))
                .totalOrders(allOrders.size())
                .completedOrders(orderRepository.countByStatus(OrderStatus.COMPLETED))
                .totalTransactionValue(totalValue)
                .productsByCategory(byCategory)
                .productsByUniversity(byUniversity)
                .build();
    }

    public List<ProductResponse> getAllProductsForAdmin(ProductStatus status) {
        List<Product> products = (status != null)
                ? productRepository.findByStatusOrderByCreatedAtDesc(status)
                : productRepository.findAllByOrderByCreatedAtDesc();
        return products.stream()
                .map(p -> dtoMapper.toProductResponse(p, false))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse approveProduct(Long productId, String adminEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        ProductStatus from = product.getStatus();
        product.setStatus(ProductStatus.APPROVED);
        product.setRejectReason(null);
        Product saved = productRepository.save(product);
        logModeration(saved, adminEmail, "APPROVE", from, ProductStatus.APPROVED, null);
        // Gửi thông báo cho seller
        notificationService.createNotification(
                saved.getSeller(),
                "PRODUCT_APPROVED",
                "Tin đăng đã được duyệt! 🎉",
                "Tin \"" + saved.getTitle() + "\" đã được admin duyệt và hiển thị trên sàn.",
                saved.getId()
        );
        return dtoMapper.toProductResponse(saved, false);
    }

    @Transactional
    public ProductResponse rejectProduct(Long productId, String reason, String adminEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        ProductStatus from = product.getStatus();
        product.setStatus(ProductStatus.REJECTED);
        String rejectReason = reason != null && !reason.isBlank() ? reason : "Vi phạm quy chuẩn cộng đồng sinh viên";
        product.setRejectReason(rejectReason);
        Product saved = productRepository.save(product);
        logModeration(saved, adminEmail, "REJECT", from, ProductStatus.REJECTED, rejectReason);
        // Gửi thông báo cho seller
        notificationService.createNotification(
                saved.getSeller(),
                "PRODUCT_REJECTED",
                "Tin đăng bị từ chối",
                "Tin \"" + saved.getTitle() + "\" chưa đạt chuẩn. Lý do: " + rejectReason,
                saved.getId()
        );
        return dtoMapper.toProductResponse(saved, false);
    }

    @Transactional
    public ProductResponse hideProduct(Long productId, boolean hidden, String adminEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        ProductStatus from = product.getStatus();
        ProductStatus to = hidden ? ProductStatus.HIDDEN : ProductStatus.APPROVED;
        product.setStatus(to);
        Product saved = productRepository.save(product);
        logModeration(saved, adminEmail, hidden ? "HIDE" : "UNHIDE", from, to, null);
        notificationService.createNotification(
                saved.getSeller(),
                hidden ? "PRODUCT_HIDDEN" : "PRODUCT_APPROVED",
                hidden ? "Tin đăng đã bị ẩn" : "Tin đăng đã được hiển thị lại",
                "Tin \"" + saved.getTitle() + "\" đã được admin "
                        + (hidden ? "tạm ẩn khỏi sàn." : "hiển thị trở lại trên sàn."),
                saved.getId()
        );
        return dtoMapper.toProductResponse(saved, false);
    }

    private void logModeration(Product product, String adminEmail, String action,
                               ProductStatus from, ProductStatus to, String reason) {
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        moderationLogRepository.save(ModerationLog.builder()
                .product(product)
                .admin(admin)
                .action(action)
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to != null ? to.name() : null)
                .reason(reason)
                .build());
    }

    public List<Map<String, Object>> getModerationLogs() {
        return moderationLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", l.getId());
                    m.put("productId", l.getProduct() != null ? l.getProduct().getId() : null);
                    m.put("productTitle", l.getProduct() != null ? l.getProduct().getTitle() : null);
                    m.put("adminName", l.getAdmin() != null ? l.getAdmin().getFullName() : "Hệ thống");
                    m.put("action", l.getAction());
                    m.put("fromStatus", l.getFromStatus());
                    m.put("toStatus", l.getToStatus());
                    m.put("reason", l.getReason());
                    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().format(FORMATTER) : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** Toàn bộ đơn hàng, lọc theo trạng thái nếu có. */
    public List<OrderResponse> getAllOrders(OrderStatus status) {
        List<OrderRequest> orders = status != null
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status)
                : orderRepository.findAll();
        return orders.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(dtoMapper::toOrderResponse)
                .collect(Collectors.toList());
    }

    public List<UserSummaryDto> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(dtoMapper::toUserSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserSummaryDto toggleUserActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên"));
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return dtoMapper.toUserSummary(userRepository.save(user));
    }

    @Transactional
    public UserSummaryDto toggleUserVerified(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên"));
        user.setVerifiedStudent(!Boolean.TRUE.equals(user.getVerifiedStudent()));
        return dtoMapper.toUserSummary(userRepository.save(user));
    }

    @Transactional
    public UserSummaryDto changeUserRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        try {
            Role role = Role.valueOf(roleName.toUpperCase());
            user.setRole(role);
            return dtoMapper.toUserSummary(userRepository.save(user));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role không hợp lệ: " + roleName);
        }
    }
}
