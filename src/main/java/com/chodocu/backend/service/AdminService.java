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
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ trang quản trị: thống kê, kiểm duyệt tin đăng, quản lý người dùng và đơn hàng.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    /** Duyệt được tin đang chờ duyệt, hoặc tin đã bị từ chối (xét lại). */
    private static final Set<ProductStatus> APPROVABLE = Set.of(ProductStatus.PENDING, ProductStatus.REJECTED);

    /** Từ chối được tin đang chờ duyệt, đang hiển thị hoặc đang ẩn. */
    private static final Set<ProductStatus> REJECTABLE =
            Set.of(ProductStatus.PENDING, ProductStatus.APPROVED, ProductStatus.HIDDEN);

    private static final int MAX_REASON_LENGTH = 255;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRequestRepository orderRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final DtoMapper dtoMapper;
    private final NotificationService notificationService;

    // ----------------------------------------------------------------
    // Thống kê
    // ----------------------------------------------------------------

    public DashboardStatsResponse getDashboardStats() {
        List<Product> allProducts = productRepository.findAll();
        List<OrderRequest> allOrders = orderRepository.findAll();

        BigDecimal totalValue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.ACCEPTED)
                .map(OrderRequest::getOfferedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byCategory = allProducts.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(p -> p.getCategory().getName(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> byUniversity = allProducts.stream()
                .filter(p -> p.getUniversity() != null)
                .collect(Collectors.groupingBy(p -> p.getUniversity().getShortName(), LinkedHashMap::new, Collectors.counting()));

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

    // ----------------------------------------------------------------
    // Xem tin đăng
    // ----------------------------------------------------------------

    public List<ProductResponse> getAllProductsForAdmin(ProductStatus status) {
        List<Product> products = (status != null)
                ? productRepository.findByStatusOrderByCreatedAtDesc(status)
                : productRepository.findAllByOrderByCreatedAtDesc();
        return products.stream()
                .map(dtoMapper::toAdminProductResponse)
                .toList();
    }

    /** Chi tiết 1 tin (mọi trạng thái) để kiểm duyệt viên xem kỹ trước khi duyệt. */
    public ProductResponse getProductForAdmin(Long productId) {
        return dtoMapper.toAdminProductResponse(findProduct(productId));
    }

    /** Lịch sử kiểm duyệt của 1 tin, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProductLogs(Long productId) {
        findProduct(productId); // báo 404 nếu tin không tồn tại
        return moderationLogRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toProductLog)
                .toList();
    }

    /** Nhật ký kiểm duyệt gần nhất trên toàn hệ thống. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModerationLogs() {
        return moderationLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(log -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", log.getId());
                    m.put("productId", log.getProduct() != null ? log.getProduct().getId() : null);
                    m.put("productTitle", log.getProduct() != null ? log.getProduct().getTitle() : null);
                    m.put("adminName", moderatorNameOf(log));
                    m.put("action", log.getAction());
                    m.put("fromStatus", log.getFromStatus());
                    m.put("toStatus", log.getToStatus());
                    m.put("reason", log.getReason());
                    m.put("createdAt", DisplayFormat.dateTime(log.getCreatedAt()));
                    return m;
                })
                .toList();
    }

    // ----------------------------------------------------------------
    // Kiểm duyệt tin đăng
    // ----------------------------------------------------------------

    @Transactional
    public ProductResponse approveProduct(Long productId, String adminEmail) {
        Product product = findProduct(productId);
        if (!APPROVABLE.contains(product.getStatus())) {
            throw new BadRequestException("Chỉ duyệt được tin đang chờ duyệt hoặc đã bị từ chối");
        }
        return dtoMapper.toAdminProductResponse(approve(product, findUserByEmail(adminEmail)));
    }

    @Transactional
    public ProductResponse rejectProduct(Long productId, String reason, String adminEmail) {
        String cleanReason = requireReason(reason);
        Product product = findProduct(productId);
        if (!REJECTABLE.contains(product.getStatus())) {
            throw new BadRequestException("Không thể từ chối tin ở trạng thái hiện tại");
        }
        return dtoMapper.toAdminProductResponse(reject(product, cleanReason, findUserByEmail(adminEmail)));
    }

    /** Duyệt hàng loạt. Tin không tồn tại hoặc sai trạng thái được bỏ qua (tính vào skipped). */
    @Transactional
    public Map<String, Integer> bulkApprove(List<Long> ids, String adminEmail) {
        User admin = findUserByEmail(adminEmail);
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);

        int processed = 0;
        for (Product product : productRepository.findAllById(uniqueIds)) {
            if (APPROVABLE.contains(product.getStatus())) {
                approve(product, admin);
                processed++;
            }
        }
        return bulkResult(processed, uniqueIds.size() - processed);
    }

    /** Từ chối hàng loạt với cùng một lý do. */
    @Transactional
    public Map<String, Integer> bulkReject(List<Long> ids, String reason, String adminEmail) {
        String cleanReason = requireReason(reason);
        User admin = findUserByEmail(adminEmail);
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);

        int processed = 0;
        for (Product product : productRepository.findAllById(uniqueIds)) {
            if (REJECTABLE.contains(product.getStatus())) {
                reject(product, cleanReason, admin);
                processed++;
            }
        }
        return bulkResult(processed, uniqueIds.size() - processed);
    }

    /** Ẩn tin đang hiển thị khỏi sàn (không xóa dữ liệu) hoặc hiện lại tin đang bị ẩn. */
    @Transactional
    public ProductResponse hideProduct(Long productId, boolean hidden, String adminEmail) {
        Product product = findProduct(productId);
        ProductStatus required = hidden ? ProductStatus.APPROVED : ProductStatus.HIDDEN;
        if (product.getStatus() != required) {
            throw new BadRequestException(hidden
                    ? "Chỉ ẩn được tin đang hiển thị trên sàn"
                    : "Tin đăng này không ở trạng thái ẩn");
        }

        ProductStatus from = product.getStatus();
        ProductStatus to = hidden ? ProductStatus.HIDDEN : ProductStatus.APPROVED;
        product.setStatus(to);
        Product saved = productRepository.save(product);

        logModeration(saved, findUserByEmail(adminEmail), hidden ? "HIDE" : "UNHIDE", from, to, null);
        notificationService.createNotification(
                saved.getSeller(),
                hidden ? "PRODUCT_HIDDEN" : "PRODUCT_APPROVED",
                hidden ? "Tin đăng đã bị ẩn" : "Tin đăng đã được hiển thị lại",
                "Tin \"" + saved.getTitle() + "\" đã được admin "
                        + (hidden ? "tạm ẩn khỏi sàn." : "hiển thị trở lại trên sàn."),
                saved.getId()
        );
        return dtoMapper.toAdminProductResponse(saved);
    }

    // ----------------------------------------------------------------
    // Đơn hàng & người dùng
    // ----------------------------------------------------------------

    /** Toàn bộ đơn hàng, lọc theo trạng thái nếu có. */
    public List<OrderResponse> getAllOrders(OrderStatus status) {
        List<OrderRequest> orders = status != null
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status)
                : orderRepository.findAll();
        return orders.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(dtoMapper::toAdminOrderResponse)
                .toList();
    }

    public List<UserSummaryDto> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(dtoMapper::toUserSummary)
                .toList();
    }

    @Transactional
    public UserSummaryDto toggleUserActive(Long userId) {
        User user = findUserById(userId);
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return dtoMapper.toUserSummary(userRepository.save(user));
    }

    @Transactional
    public UserSummaryDto changeUserRole(Long userId, String roleName) {
        User user = findUserById(userId);
        try {
            user.setRole(Role.valueOf(String.valueOf(roleName).trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role không hợp lệ: " + roleName);
        }
        return dtoMapper.toUserSummary(userRepository.save(user));
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    private Product approve(Product product, User admin) {
        ProductStatus from = product.getStatus();
        product.setStatus(ProductStatus.APPROVED);
        product.setRejectReason(null);
        Product saved = productRepository.save(product);

        logModeration(saved, admin, "APPROVE", from, ProductStatus.APPROVED, null);
        notificationService.createNotification(
                saved.getSeller(),
                "PRODUCT_APPROVED",
                "Tin đăng đã được duyệt! 🎉",
                "Tin \"" + saved.getTitle() + "\" đã được admin duyệt và hiển thị trên sàn.",
                saved.getId()
        );
        return saved;
    }

    private Product reject(Product product, String reason, User admin) {
        ProductStatus from = product.getStatus();
        product.setStatus(ProductStatus.REJECTED);
        product.setRejectReason(reason);
        Product saved = productRepository.save(product);

        logModeration(saved, admin, "REJECT", from, ProductStatus.REJECTED, reason);
        notificationService.createNotification(
                saved.getSeller(),
                "PRODUCT_REJECTED",
                "Tin đăng bị từ chối",
                "Tin \"" + saved.getTitle() + "\" chưa đạt chuẩn. Lý do: " + reason,
                saved.getId()
        );
        return saved;
    }

    private void logModeration(Product product, User admin, String action,
                               ProductStatus from, ProductStatus to, String reason) {
        moderationLogRepository.save(ModerationLog.builder()
                .product(product)
                .admin(admin)
                .action(action)
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to != null ? to.name() : null)
                .reason(reason)
                .build());
    }

    private Map<String, Object> toProductLog(ModerationLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("action", log.getAction());
        m.put("reason", log.getReason());
        m.put("moderatorName", moderatorNameOf(log));
        m.put("fromStatus", log.getFromStatus());
        m.put("toStatus", log.getToStatus());
        m.put("createdAt", DisplayFormat.dateTime(log.getCreatedAt()));
        return m;
    }

    private String moderatorNameOf(ModerationLog log) {
        return log.getAdmin() != null ? log.getAdmin().getFullName() : "Hệ thống";
    }

    /** Lý do từ chối là bắt buộc để người bán biết cần sửa gì. */
    private String requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException("Vui lòng nhập lý do từ chối");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Lý do từ chối tối đa " + MAX_REASON_LENGTH + " ký tự");
        }
        return trimmed;
    }

    private Map<String, Integer> bulkResult(int processed, int skipped) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("processed", processed);
        result.put("skipped", skipped);
        return result;
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị"));
    }
}
