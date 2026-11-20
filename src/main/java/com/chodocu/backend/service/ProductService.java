package com.chodocu.backend.service;

import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.product.ProductRequest;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.Category;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.University;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.ProductCondition;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.CategoryRepository;
import com.chodocu.backend.repository.ChatMessageRepository;
import com.chodocu.backend.repository.ModerationLogRepository;
import com.chodocu.backend.repository.OrderRequestRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.ReportRepository;
import com.chodocu.backend.repository.UniversityRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nghiệp vụ tin đăng: tìm kiếm, xem chi tiết, đăng, sửa, đổi trạng thái và xóa.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    /** Chỉ cho phép sắp xếp theo các cột này (client gửi tên cột lạ sẽ dùng createdAt). */
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price", "viewCount");
    private static final int MAX_PAGE_SIZE = 50;

    /** Người bán chỉ được tự chuyển trạng thái tin của mình theo các bước dưới đây. */
    private static final Map<ProductStatus, Set<ProductStatus>> OWNER_TRANSITIONS = Map.of(
            ProductStatus.APPROVED, Set.of(ProductStatus.HIDDEN, ProductStatus.SOLD),
            ProductStatus.HIDDEN, Set.of(ProductStatus.APPROVED),
            ProductStatus.IN_TRANSACTION, Set.of(ProductStatus.SOLD)
    );

    /** Tin ở các trạng thái này chỉ người bán và admin/kiểm duyệt viên được xem. */
    private static final Set<ProductStatus> PRIVATE_STATUSES =
            Set.of(ProductStatus.PENDING, ProductStatus.REJECTED, ProductStatus.HIDDEN);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final OrderRequestRepository orderRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReportRepository reportRepository;
    private final DtoMapper dtoMapper;
    private final PlatformSettingService platformSettingService;
    private final AdminStatsCache adminStatsCache;

    // ----------------------------------------------------------------
    // Xem tin
    // ----------------------------------------------------------------

    /** Tìm kiếm tin đã duyệt (feed trang chủ) với bộ lọc và phân trang. */
    public PageResponse<ProductResponse> searchProducts(
            String keyword,
            Long categoryId,
            Long universityId,
            ProductCondition condition,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean urgentOnly,
            int page,
            int size,
            String sortBy,
            String sortDir,
            Long currentUserId
    ) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, buildSort(sortBy, sortDir));
        String cleanKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(java.util.Locale.ROOT) : "";

        Page<Product> productPage = productRepository.searchApprovedProducts(
                cleanKeyword, categoryId, universityId, condition, minPrice, maxPrice, urgentOnly, pageable
        );

        List<Product> products = productPage.getContent();
        Set<Long> favoritedIds = (currentUserId != null && !products.isEmpty())
                ? wishlistRepository.findFavoritedProductIds(
                        currentUserId, products.stream().map(Product::getId).toList())
                : Set.of();

        List<ProductResponse> content = products.stream()
                .map(p -> dtoMapper.toProductResponse(p, favoritedIds.contains(p.getId())))
                .toList();

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    /** Chi tiết tin. Tin chưa duyệt / bị từ chối / đang ẩn chỉ người bán và staff được xem. */
    @Transactional
    public ProductResponse getProductById(Long id, Long currentUserId) {
        Product product = findProduct(id);
        User viewer = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        boolean isOwner = viewer != null && isOwner(product, viewer);

        if (PRIVATE_STATUSES.contains(product.getStatus()) && !isOwner && !isStaff(viewer)) {
            throw new ResourceNotFoundException("Không tìm thấy tin đăng với mã #" + id);
        }

        // Chỉ đếm lượt xem từ người khác, không đếm khi người bán tự xem tin của mình
        if (!isOwner) {
            product.setViewCount(product.getViewCount() + 1);
            productRepository.save(product);
        }
        return dtoMapper.toProductResponse(product, isFavorited(currentUserId, id));
    }

    public List<ProductResponse> getMyPostedProducts(String sellerEmail) {
        User seller = findUserByEmail(sellerEmail);
        return productRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(p -> dtoMapper.toProductResponse(p, false))
                .toList();
    }

    // ----------------------------------------------------------------
    // Đăng / sửa tin
    // ----------------------------------------------------------------

    @Transactional
    public ProductResponse createProduct(ProductRequest request, String sellerEmail) {
        User seller = findUserByEmail(sellerEmail);

        // Quyền bỏ qua bước duyệt do server quyết định, không tin dữ liệu từ client
        boolean skipModeration = isStaff(seller) || !platformSettingService.isPostApprovalRequired();

        Product product = Product.builder()
                .seller(seller)
                .status(skipModeration ? ProductStatus.APPROVED : ProductStatus.PENDING)
                .build();
        applyRequest(product, request);

        Product saved = productRepository.save(product);
        adminStatsCache.invalidate();
        return dtoMapper.toProductResponse(saved, false);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request, String userEmail) {
        Product product = findProduct(productId);
        User user = findUserByEmail(userEmail);
        requireOwnerOrStaff(product, user, "Bạn chỉ có thể chỉnh sửa tin đăng của chính mình");

        applyRequest(product, request);

        // Người bán sửa nội dung -> tin phải được duyệt lại (trừ khi đang giao dịch / đã bán)
        if (needsReModeration(product, user)) {
            product.setStatus(ProductStatus.PENDING);
            product.setRejectReason(null);
        }
        Product saved = productRepository.save(product);
        adminStatsCache.invalidate();
        return dtoMapper.toProductResponse(saved, false);
    }

    // ----------------------------------------------------------------
    // Đổi trạng thái / xóa tin
    // ----------------------------------------------------------------

    /**
     * Người bán chỉ được: ẩn tin, hiện lại tin, đánh dấu đã bán (xem OWNER_TRANSITIONS).
     * Admin / kiểm duyệt viên được chuyển sang bất kỳ trạng thái nào.
     */
    @Transactional
    public ProductResponse updateProductStatus(Long productId, ProductStatus newStatus, String userEmail) {
        Product product = findProduct(productId);
        User user = findUserByEmail(userEmail);
        requireOwnerOrStaff(product, user, "Bạn không có quyền đổi trạng thái tin đăng này");

        if (!isStaff(user) && !isAllowedOwnerTransition(product.getStatus(), newStatus)) {
            throw new BadRequestException("Không thể chuyển trạng thái tin đăng này");
        }

        if (newStatus == ProductStatus.SOLD && product.getStatus() != ProductStatus.SOLD) {
            User seller = product.getSeller();
            seller.setCompletedDeals(seller.getCompletedDeals() + 1);
            userRepository.save(seller);
        }
        product.setStatus(newStatus);
        Product saved = productRepository.save(product);
        adminStatsCache.invalidate();
        return dtoMapper.toProductResponse(saved, false);
    }

    /** Xóa tin chưa phát sinh giao dịch. Tin đã có đơn hàng thì chỉ được ẩn để giữ lịch sử. */
    @Transactional
    public void deleteProduct(Long productId, String userEmail) {
        Product product = findProduct(productId);
        User user = findUserByEmail(userEmail);
        requireOwnerOrStaff(product, user, "Bạn chỉ có thể xóa tin đăng của chính mình");

        if (orderRepository.existsByProductId(productId)) {
            throw new BadRequestException("Tin đã phát sinh giao dịch, hãy ẩn tin thay vì xóa");
        }

        // Gỡ các dữ liệu đang trỏ tới tin đăng để không bị lỗi khóa ngoại khi xóa
        moderationLogRepository.deleteByProductId(productId);
        chatMessageRepository.clearProductReference(productId);
        reportRepository.clearProductReference(productId);
        wishlistRepository.deleteByProductId(productId);
        productRepository.delete(product);
        adminStatsCache.invalidate();
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    /** Chép dữ liệu form vào entity, kèm kiểm tra danh mục, trường, điểm hẹn, giá và ảnh. */
    private void applyRequest(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));
        University university = universityRepository.findById(request.getUniversityId())
                .orElseThrow(() -> new ResourceNotFoundException("Trường đại học không tồn tại"));

        String pickupSpot = request.getPickupSpot().trim();
        if (!university.hasMeetupSpot(pickupSpot)) {
            throw new BadRequestException("Điểm hẹn không thuộc danh sách điểm hẹn của trường đã chọn");
        }

        boolean isFree = Boolean.TRUE.equals(request.getIsFree());
        if (!isFree && request.getPrice().signum() <= 0) {
            throw new BadRequestException("Giá bán phải lớn hơn 0 hoặc chọn Tặng miễn phí");
        }

        List<String> imageUrls = cleanImageUrls(request.getImageUrls());
        if (imageUrls.isEmpty()) {
            throw new BadRequestException("Vui lòng thêm ít nhất 1 ảnh thật của sản phẩm");
        }

        product.setTitle(request.getTitle().trim());
        product.setDescription(blankToNull(request.getDescription()));
        product.setIsFree(isFree);
        product.setPrice(isFree ? BigDecimal.ZERO : request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setIsUrgent(Boolean.TRUE.equals(request.getIsUrgent()));
        product.setItemCondition(request.getCondition());
        product.setConditionNote(blankToNull(request.getConditionNote()));
        product.setPassReason(blankToNull(request.getPassReason()));
        product.setPickupSpot(pickupSpot);
        product.setCategory(category);
        product.setUniversity(university);
        product.getImageUrls().clear();
        product.getImageUrls().addAll(imageUrls);
    }

    private boolean needsReModeration(Product product, User editor) {
        return !isStaff(editor)
                && platformSettingService.isPostApprovalRequired()
                && product.getStatus() != ProductStatus.IN_TRANSACTION
                && product.getStatus() != ProductStatus.SOLD;
    }

    private boolean isAllowedOwnerTransition(ProductStatus from, ProductStatus to) {
        return OWNER_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private void requireOwnerOrStaff(Product product, User user, String errorMessage) {
        if (!isOwner(product, user) && !isStaff(user)) {
            throw new BadRequestException(errorMessage);
        }
    }

    private boolean isOwner(Product product, User user) {
        return product.getSeller().getId().equals(user.getId());
    }

    /** Admin hoặc kiểm duyệt viên. */
    private boolean isStaff(User user) {
        return user != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR);
    }

    private boolean isFavorited(Long userId, Long productId) {
        return userId != null && wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        return "asc".equalsIgnoreCase(sortDir) ? Sort.by(field).ascending() : Sort.by(field).descending();
    }

    private List<String> cleanImageUrls(List<String> urls) {
        return urls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Product findProduct(Long id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng với mã #" + id));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
