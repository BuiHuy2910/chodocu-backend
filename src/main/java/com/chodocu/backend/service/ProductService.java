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
import com.chodocu.backend.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final DtoMapper dtoMapper;

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
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productRepository.searchApprovedProducts(
                keyword, categoryId, universityId, condition, minPrice, maxPrice, urgentOnly, pageable
        );

        List<ProductResponse> content = productPage.getContent().stream()
                .map(p -> {
                    boolean fav = currentUserId != null && wishlistRepository.existsByUserIdAndProductId(currentUserId, p.getId());
                    return dtoMapper.toProductResponse(p, fav);
                })
                .collect(Collectors.toList());

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    @Transactional
    public ProductResponse getProductById(Long id, Long currentUserId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng với mã #" + id));
        product.setViewCount(product.getViewCount() + 1);
        productRepository.save(product);

        boolean fav = currentUserId != null && wishlistRepository.existsByUserIdAndProductId(currentUserId, product.getId());
        return dtoMapper.toProductResponse(product, fav);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, String sellerEmail, boolean autoApprove) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người bán"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));

        University university = universityRepository.findById(request.getUniversityId())
                .orElseThrow(() -> new ResourceNotFoundException("Trường đại học không tồn tại"));

        List<String> images = request.getImageUrls() != null && !request.getImageUrls().isEmpty()
                ? new ArrayList<>(request.getImageUrls())
                : List.of("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800&auto=format&fit=crop&q=80");

        BigDecimal finalPrice = Boolean.TRUE.equals(request.getIsFree()) ? BigDecimal.ZERO : request.getPrice();

        Product product = Product.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .price(finalPrice)
                .originalPrice(request.getOriginalPrice())
                .isFree(Boolean.TRUE.equals(request.getIsFree()))
                .isUrgent(Boolean.TRUE.equals(request.getIsUrgent()))
                .itemCondition(request.getCondition() != null ? request.getCondition() : ProductCondition.LIKE_NEW_95)
                .conditionNote(request.getConditionNote() != null ? request.getConditionNote() : "Mới 95%")
                .passReason(request.getPassReason() != null ? request.getPassReason() : "Dọn phòng KTX / Chuyển trọ")
                .pickupSpot(request.getPickupSpot() != null ? request.getPickupSpot() : "Sảnh KTX Khu B - ĐHQG")
                .status(autoApprove ? ProductStatus.APPROVED : ProductStatus.PENDING)
                .viewCount(1)
                .favoriteCount(0)
                .category(category)
                .university(university)
                .seller(seller)
                .imageUrls(images)
                .build();

        Product saved = productRepository.save(product);
        return dtoMapper.toProductResponse(saved, false);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request, String userEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!product.getSeller().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Bạn chỉ có thể chỉnh sửa tin đăng của chính mình");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));
        University university = universityRepository.findById(request.getUniversityId())
                .orElseThrow(() -> new ResourceNotFoundException("Trường đại học không tồn tại"));

        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setIsFree(Boolean.TRUE.equals(request.getIsFree()));
        product.setPrice(Boolean.TRUE.equals(request.getIsFree()) ? BigDecimal.ZERO : request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setIsUrgent(Boolean.TRUE.equals(request.getIsUrgent()));
        if (request.getCondition() != null) product.setItemCondition(request.getCondition());
        if (request.getConditionNote() != null) product.setConditionNote(request.getConditionNote());
        if (request.getPassReason() != null) product.setPassReason(request.getPassReason());
        if (request.getPickupSpot() != null) product.setPickupSpot(request.getPickupSpot());
        product.setCategory(category);
        product.setUniversity(university);
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            product.setImageUrls(new ArrayList<>(request.getImageUrls()));
        }

        return dtoMapper.toProductResponse(productRepository.save(product), false);
    }

    @Transactional
    public ProductResponse updateProductStatus(Long productId, ProductStatus newStatus, String userEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!product.getSeller().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Bạn không có quyền đổi trạng thái tin đăng này");
        }

        product.setStatus(newStatus);
        if (newStatus == ProductStatus.SOLD) {
            User seller = product.getSeller();
            seller.setCompletedDeals(seller.getCompletedDeals() + 1);
            userRepository.save(seller);
        }

        return dtoMapper.toProductResponse(productRepository.save(product), false);
    }

    @Transactional
    public void deleteProduct(Long productId, String userEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!product.getSeller().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Bạn chỉ có thể xóa tin đăng của chính mình");
        }

        wishlistRepository.deleteByProductId(productId);
        productRepository.delete(product);
    }

    public List<ProductResponse> getMyPostedProducts(String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return productRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(p -> dtoMapper.toProductResponse(p, false))
                .collect(Collectors.toList());
    }
}
