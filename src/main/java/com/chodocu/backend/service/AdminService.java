package com.chodocu.backend.service;

import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.admin.DashboardStatsResponse;
import com.chodocu.backend.dto.admin.PendingQueueResponse;
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
import com.chodocu.backend.repository.ReportRepository;
import com.chodocu.backend.repository.TransactionRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.security.CustomUserDetailsService;
import com.chodocu.backend.util.DisplayFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Nghiệp vụ trang quản trị: thống kê (tổng hợp SQL trực tiếp), kiểm duyệt tin đăng,
 * phân trang danh sách sản phẩm / đơn hàng / người dùng / nhật ký.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    /** Duyệt được tin đang chờ duyệt, hoặc tin đã bị từ chối (xét lại). */
    private static final Set<ProductStatus> APPROVABLE = Set.of(ProductStatus.PENDING, ProductStatus.REJECTED);

    /** Từ chối được tin đang chờ duyệt, đang hiển thị hoặc đang ẩn. */
    private static final Set<ProductStatus> REJECTABLE =
            Set.of(ProductStatus.PENDING, ProductStatus.APPROVED, ProductStatus.HIDDEN);

    /** Các trạng thái tính là "đã qua kiểm duyệt" cho thẻ thống kê người bán. */
    private static final Set<ProductStatus> APPROVED_FAMILY =
            Set.of(ProductStatus.APPROVED, ProductStatus.IN_TRANSACTION, ProductStatus.SOLD);

    private static final Set<String> SORTABLE_PRODUCT_FIELDS = Set.of("createdAt", "price", "viewCount", "id");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 255;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRequestRepository orderRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ReportRepository reportRepository;
    private final TransactionRepository transactionRepository;
    private final CustomUserDetailsService userDetailsService;
    private final DtoMapper dtoMapper;
    private final NotificationService notificationService;
    private final AdminStatsCache adminStatsCache;
    private final ObjectMapper objectMapper;

    // ----------------------------------------------------------------
    // Thống kê — Gộp toàn bộ vào 1 câu SQL duy nhất + cache RAM 15 giây
    // ----------------------------------------------------------------

    public DashboardStatsResponse getDashboardStats(boolean includeFinancial) {
        DashboardStatsResponse cached = adminStatsCache.getStats(includeFinancial);
        if (cached != null) {
            return cached;
        }

        Object[] s = productRepository.fetchDashboardOverviewCounts().get(0);
        Map<String, Long> byCategory = parseCountPairsJson(s[12]);
        Map<String, Long> byUniversity = parseCountPairsJson(s[13]);

        DashboardStatsResponse fresh = DashboardStatsResponse.builder()
                .totalStudents(toLong(s[0]))
                .totalModerators(toLong(s[1]))
                .totalProducts(toLong(s[2]))
                .pendingProducts(toLong(s[3]))
                .approvedProducts(toLong(s[4]))
                .inTransactionProducts(toLong(s[5]))
                .soldProducts(toLong(s[6]))
                .totalOrders(toLong(s[7]))
                .completedOrders(toLong(s[8]))
                .totalTransactionValue(toBigDecimal(s[9]))
                .pendingReports(toLong(s[10]))
                .totalPlatformFees(includeFinancial ? toBigDecimal(s[11]) : null)
                .productsByCategory(byCategory)
                .productsByUniversity(byUniversity)
                .build();

        adminStatsCache.putStats(includeFinancial, fresh);
        return fresh;
    }

    private Map<String, Long> parseCountPairsJson(Object raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (raw == null) return map;
        try {
            List<List<Object>> pairs = objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {});
            for (List<Object> pair : pairs) {
                if (pair.size() >= 2 && pair.get(0) != null) {
                    map.put(String.valueOf(pair.get(0)), toLong(pair.get(1)));
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private List<String> parseStringListJson(Object raw) {
        if (raw == null) return List.of();
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.longValue());
        return BigDecimal.ZERO;
    }

    // ----------------------------------------------------------------
    // Xem tin đăng & Hàng chờ duyệt (phân trang)
    // ----------------------------------------------------------------

    public PageResponse<ProductResponse> getProductsPage(
            ProductStatus status,
            String keyword,
            String categoryName,
            String schoolName,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        String cleanKeyword = normalizeKeyword(keyword);
        Long exactId = parseNumericId(keyword);
        String cleanCat = StringUtils.hasText(categoryName) ? categoryName.trim() : "";
        String cleanSchool = StringUtils.hasText(schoolName) ? schoolName.trim() : "";
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, 20), buildProductSort(sortBy, sortDir));

        Page<Product> productPage = productRepository.searchAdminProducts(
                status, cleanKeyword, exactId, cleanCat, cleanSchool, pageable);
        return PageResponse.of(productPage, dtoMapper::toAdminProductResponse);
    }

    /**
     * Tải 1 trang hàng chờ duyệt kèm đầy đủ ngữ cảnh người bán & lịch sử duyệt trong 1 lần gọi.
     */
    public PendingQueueResponse getPendingQueue(
            String keyword,
            String categoryName,
            String schoolName,
            String sort,
            int page,
            int size
    ) {
        String cleanKeyword = normalizeKeyword(keyword);
        Long exactId = parseNumericId(keyword);
        String cleanCat = StringUtils.hasText(categoryName) ? categoryName.trim() : "";
        String cleanSchool = StringUtils.hasText(schoolName) ? schoolName.trim() : "";
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, 20), buildPendingSort(sort));

        Page<Product> pendingPage = productRepository.searchAdminProducts(
                ProductStatus.PENDING, cleanKeyword, exactId, cleanCat, cleanSchool, pageable);

        List<Product> products = pendingPage.getContent();
        PageResponse<ProductResponse> pageDto = PageResponse.of(pendingPage, dtoMapper::toAdminProductResponse);

        boolean unfiltered = cleanKeyword.isEmpty() && cleanCat.isEmpty() && cleanSchool.isEmpty();
        long totalPending;
        List<String> categories;
        List<String> schools;

        if (unfiltered && pendingPage.getTotalElements() == 0) {
            totalPending = 0L;
            categories = List.of();
            schools = List.of();
            adminStatsCache.putPendingMeta(new AdminStatsCache.PendingFilterMeta(0L, categories, schools));
        } else {
            AdminStatsCache.PendingFilterMeta meta = adminStatsCache.getPendingMeta();
            if (meta == null) {
                Object[] m = productRepository.fetchPendingFilterMeta().get(0);
                meta = new AdminStatsCache.PendingFilterMeta(
                        toLong(m[0]),
                        parseStringListJson(m[1]),
                        parseStringListJson(m[2])
                );
                adminStatsCache.putPendingMeta(meta);
            }
            totalPending = unfiltered ? pendingPage.getTotalElements() : meta.totalPending();
            categories = meta.categories();
            schools = meta.schools();
        }

        Set<Long> sellerIds = products.stream()
                .map(p -> p.getSeller() != null ? p.getSeller().getId() : null)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Map<String, Long>> sellerStats = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> sellerSiblings = new LinkedHashMap<>();
        if (!sellerIds.isEmpty()) {
            for (Long sid : sellerIds) {
                Map<String, Long> blank = new LinkedHashMap<>();
                blank.put("total", 0L);
                blank.put("approved", 0L);
                blank.put("rejected", 0L);
                blank.put("pending", 0L);
                sellerStats.put(sid, blank);
                sellerSiblings.put(sid, new ArrayList<>());
            }

            for (Object[] row : productRepository.countBySellerIdsGroupByStatus(sellerIds)) {
                Long sid = (Long) row[0];
                ProductStatus st = (ProductStatus) row[1];
                long count = ((Number) row[2]).longValue();
                Map<String, Long> m = sellerStats.get(sid);
                if (m != null) {
                    m.put("total", m.get("total") + count);
                    if (APPROVED_FAMILY.contains(st)) m.put("approved", m.get("approved") + count);
                    if (st == ProductStatus.REJECTED) m.put("rejected", m.get("rejected") + count);
                    if (st == ProductStatus.PENDING) m.put("pending", m.get("pending") + count);
                }
            }

            for (Object[] row : productRepository.findSiblingSummariesBySellerIds(sellerIds)) {
                Long pid = (Long) row[0];
                Long sid = (Long) row[1];
                String title = (String) row[2];
                ProductStatus st = (ProductStatus) row[3];
                List<Map<String, Object>> list = sellerSiblings.get(sid);
                if (list != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", pid);
                    item.put("title", title);
                    item.put("status", st != null ? st.name() : null);
                    list.add(item);
                }
            }
        }

        Map<Long, List<Map<String, Object>>> logsByProduct = new LinkedHashMap<>();
        if (!products.isEmpty()) {
            List<Long> productIds = products.stream().map(Product::getId).toList();
            for (Long pid : productIds) {
                logsByProduct.put(pid, new ArrayList<>());
            }
            for (Object[] r : moderationLogRepository.findRowsByProductIds(productIds)) {
                Long pid = (Long) r[0];
                List<Map<String, Object>> logs = logsByProduct.get(pid);
                if (logs != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r[1]);
                    m.put("action", r[2]);
                    m.put("reason", r[3]);
                    m.put("moderatorName", r[4] != null ? r[4] : "Hệ thống");
                    m.put("fromStatus", r[5]);
                    m.put("toStatus", r[6]);
                    m.put("createdAt", DisplayFormat.dateTime((LocalDateTime) r[7]));
                    logs.add(m);
                }
            }
        }

        return PendingQueueResponse.builder()
                .page(pageDto)
                .totalPending(totalPending)
                .categories(categories)
                .schools(schools)
                .sellerStats(sellerStats)
                .sellerSiblings(sellerSiblings)
                .logsByProduct(logsByProduct)
                .build();
    }

    /** Chi tiết 1 tin (mọi trạng thái) để kiểm duyệt viên xem kỹ trước khi duyệt. */
    public ProductResponse getProductForAdmin(Long productId) {
        return dtoMapper.toAdminProductResponse(findProduct(productId));
    }

    /** Lịch sử kiểm duyệt của 1 tin, mới nhất trước. */
    public List<Map<String, Object>> getProductLogs(Long productId) {
        List<ModerationLog> logs = moderationLogRepository.findByProductIdWithAdmin(productId);
        if (logs.isEmpty() && !productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy tin đăng");
        }
        return logs.stream().map(this::toProductLog).toList();
    }

    /** Nhật ký kiểm duyệt toàn hệ thống (phân trang, không N+1). */
    public PageResponse<Map<String, Object>> getModerationLogsPage(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, 20));
        Page<Object[]> rows = moderationLogRepository.findAdminLogRows(pageable);
        return PageResponse.of(rows, r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("productId", r[1]);
            m.put("productTitle", r[2]);
            m.put("adminName", r[3] != null ? r[3] : "Hệ thống");
            m.put("action", r[4]);
            m.put("fromStatus", r[5]);
            m.put("toStatus", r[6]);
            m.put("reason", r[7]);
            m.put("createdAt", DisplayFormat.dateTime((LocalDateTime) r[8]));
            return m;
        });
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
        for (Product product : productRepository.findWithDetailsAllByIdIn(uniqueIds)) {
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
        for (Product product : productRepository.findWithDetailsAllByIdIn(uniqueIds)) {
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
        adminStatsCache.invalidate();

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
    // Đơn hàng & người dùng (phân trang)
    // ----------------------------------------------------------------

    public PageResponse<OrderResponse> getOrdersPage(OrderStatus status, String keyword, int page, int size) {
        String cleanKeyword = normalizeKeyword(keyword);
        Long exactId = parseNumericId(keyword);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, 15), Sort.by("createdAt").descending());
        Page<OrderRequest> orderPage = orderRepository.searchAdminOrders(status, cleanKeyword, exactId, pageable);
        return PageResponse.of(orderPage, dtoMapper::toAdminOrderResponse);
    }

    public PageResponse<UserSummaryDto> getUsersPage(Role role, String keyword, int page, int size) {
        String cleanKeyword = normalizeKeyword(keyword);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, 15), Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.searchUsers(role, cleanKeyword, pageable);
        return PageResponse.of(userPage, dtoMapper::toUserSummary);
    }

    @Transactional
    public UserSummaryDto toggleUserActive(Long userId) {
        User user = findUserById(userId);
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        User saved = userRepository.save(user);
        userDetailsService.evict(saved.getEmail());
        return dtoMapper.toUserSummary(saved);
    }

    @Transactional
    public UserSummaryDto changeUserRole(Long userId, String roleName) {
        User user = findUserById(userId);
        try {
            user.setRole(Role.valueOf(String.valueOf(roleName).trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role không hợp lệ: " + roleName);
        }
        User saved = userRepository.save(user);
        userDetailsService.evict(saved.getEmail());
        return dtoMapper.toUserSummary(saved);
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    private Sort buildProductSort(String sortBy, String sortDir) {
        String field = SORTABLE_PRODUCT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        return "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(field).ascending().and(Sort.by("id").ascending())
                : Sort.by(field).descending().and(Sort.by("id").descending());
    }

    private Sort buildPendingSort(String sort) {
        if (sort == null) return Sort.by("createdAt").ascending().and(Sort.by("id").ascending());
        return switch (sort) {
            case "newest" -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
            case "price-asc" -> Sort.by("price").ascending().and(Sort.by("createdAt").ascending());
            case "price-desc" -> Sort.by("price").descending().and(Sort.by("createdAt").ascending());
            default -> Sort.by("createdAt").ascending().and(Sort.by("id").ascending());
        };
    }

    private int clampSize(int size, int defaultSize) {
        if (size <= 0) return defaultSize;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) return "";
        String t = keyword.trim();
        if (t.startsWith("#") && t.length() > 1 && t.substring(1).chars().allMatch(Character::isDigit)) {
            return t.substring(1);
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private Long parseNumericId(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        if (t.startsWith("#")) t = t.substring(1).trim();
        if (t.isEmpty() || t.length() > 18 || !t.chars().allMatch(Character::isDigit)) return null;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Product approve(Product product, User admin) {
        ProductStatus from = product.getStatus();
        product.setStatus(ProductStatus.APPROVED);
        product.setRejectReason(null);
        Product saved = productRepository.save(product);
        adminStatsCache.invalidate();

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
        adminStatsCache.invalidate();

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
        return productRepository.findWithDetailsById(productId)
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
