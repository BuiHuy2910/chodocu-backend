package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.DashboardStatsResponse;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.dto.product.ProductResponse;
import com.chodocu.backend.entity.OrderRequest;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.OrderRequestRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRequestRepository orderRepository;
    private final DtoMapper dtoMapper;

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
    public ProductResponse approveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        product.setStatus(ProductStatus.APPROVED);
        product.setRejectReason(null);
        return dtoMapper.toProductResponse(productRepository.save(product), false);
    }

    @Transactional
    public ProductResponse rejectProduct(Long productId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng"));
        product.setStatus(ProductStatus.REJECTED);
        product.setRejectReason(reason != null && !reason.isBlank() ? reason : "Vi phạm quy chuẩn cộng đồng sinh viên");
        return dtoMapper.toProductResponse(productRepository.save(product), false);
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
}
