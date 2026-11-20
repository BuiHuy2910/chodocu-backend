package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Product;
import com.chodocu.backend.enums.ProductCondition;
import com.chodocu.backend.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' " +
            "AND (:keyword = '' OR LOWER(p.title) LIKE CONCAT('%', :keyword, '%') OR LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:universityId IS NULL OR p.university.id = :universityId) " +
            "AND (:condition IS NULL OR p.itemCondition = :condition) " +
            "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
            "AND (:urgentOnly IS NULL OR :urgentOnly = false OR p.isUrgent = true)")
    Page<Product> searchApprovedProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("universityId") Long universityId,
            @Param("condition") ProductCondition condition,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("urgentOnly") Boolean urgentOnly,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    @Query("SELECT p FROM Product p LEFT JOIN p.category c LEFT JOIN p.university u JOIN p.seller s " +
            "WHERE (:status IS NULL OR p.status = :status) " +
            "AND (:categoryName = '' OR c.name = :categoryName) " +
            "AND (:schoolName = '' OR u.shortName = :schoolName OR u.name = :schoolName) " +
            "AND (:keyword = '' OR (:exactId IS NOT NULL AND p.id = :exactId) " +
            "OR LOWER(p.title) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(s.fullName) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(s.email) LIKE CONCAT('%', :keyword, '%') " +
            "OR LOWER(COALESCE(c.name, '')) LIKE CONCAT('%', :keyword, '%'))")
    Page<Product> searchAdminProducts(
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            @Param("exactId") Long exactId,
            @Param("categoryName") String categoryName,
            @Param("schoolName") String schoolName,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    Optional<Product> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    List<Product> findWithDetailsAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    List<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);

    @EntityGraph(attributePaths = {"category", "university", "seller", "seller.university"})
    List<Product> findAllByOrderByCreatedAtDesc();

    long countByStatus(ProductStatus status);

    @Query(value = "SELECT " +
            "(SELECT COUNT(*) FROM users WHERE role = 'STUDENT'), " +
            "(SELECT COUNT(*) FROM users WHERE role = 'MODERATOR'), " +
            "(SELECT COUNT(*) FROM products), " +
            "(SELECT COUNT(*) FROM products WHERE status = 'PENDING'), " +
            "(SELECT COUNT(*) FROM products WHERE status = 'APPROVED'), " +
            "(SELECT COUNT(*) FROM products WHERE status = 'IN_TRANSACTION'), " +
            "(SELECT COUNT(*) FROM products WHERE status = 'SOLD'), " +
            "(SELECT COUNT(*) FROM order_requests), " +
            "(SELECT COUNT(*) FROM order_requests WHERE status = 'COMPLETED'), " +
            "(SELECT COALESCE(SUM(offered_price), 0) FROM order_requests WHERE status IN ('COMPLETED', 'ACCEPTED')), " +
            "(SELECT COUNT(*) FROM reports WHERE status = 'PENDING'), " +
            "(SELECT COALESCE(SUM(platform_fee_amount), 0) FROM transactions), " +
            "(SELECT COALESCE(CAST(json_agg(json_build_array(name, cnt)) AS text), '[]') FROM (" +
            "  SELECT c.name AS name, COUNT(p.id) AS cnt FROM products p JOIN categories c ON c.id = p.category_id GROUP BY c.name ORDER BY cnt DESC" +
            ") cat_sub), " +
            "(SELECT COALESCE(CAST(json_agg(json_build_array(short_name, cnt)) AS text), '[]') FROM (" +
            "  SELECT u.short_name AS short_name, COUNT(p.id) AS cnt FROM products p JOIN universities u ON u.id = p.university_id GROUP BY u.short_name ORDER BY cnt DESC" +
            ") uni_sub)",
            nativeQuery = true)
    List<Object[]> fetchDashboardOverviewCounts();

    @Query(value = "SELECT " +
            "(SELECT COUNT(*) FROM products WHERE status = 'PENDING'), " +
            "(SELECT COALESCE(CAST(json_agg(name) AS text), '[]') FROM (" +
            "  SELECT DISTINCT c.name AS name FROM products p JOIN categories c ON c.id = p.category_id WHERE p.status = 'PENDING' ORDER BY c.name ASC" +
            ") c_sub), " +
            "(SELECT COALESCE(CAST(json_agg(short_name) AS text), '[]') FROM (" +
            "  SELECT DISTINCT u.short_name AS short_name FROM products p JOIN universities u ON u.id = p.university_id WHERE p.status = 'PENDING' ORDER BY u.short_name ASC" +
            ") u_sub)",
            nativeQuery = true)
    List<Object[]> fetchPendingFilterMeta();

    @Query("SELECT p.status, COUNT(p) FROM Product p GROUP BY p.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT c.name, COUNT(p) FROM Product p JOIN p.category c GROUP BY c.name ORDER BY COUNT(p) DESC")
    List<Object[]> countGroupByCategory();

    @Query("SELECT u.shortName, COUNT(p) FROM Product p JOIN p.university u GROUP BY u.shortName ORDER BY COUNT(p) DESC")
    List<Object[]> countGroupByUniversity();

    @Query("SELECT DISTINCT c.name FROM Product p JOIN p.category c WHERE p.status = 'PENDING' ORDER BY c.name ASC")
    List<String> findDistinctPendingCategories();

    @Query("SELECT DISTINCT u.shortName FROM Product p JOIN p.university u WHERE p.status = 'PENDING' ORDER BY u.shortName ASC")
    List<String> findDistinctPendingSchools();

    @Query("SELECT p.seller.id, p.status, COUNT(p) FROM Product p WHERE p.seller.id IN :sellerIds GROUP BY p.seller.id, p.status")
    List<Object[]> countBySellerIdsGroupByStatus(@Param("sellerIds") Collection<Long> sellerIds);

    @Query("SELECT p.id, p.seller.id, p.title, p.status FROM Product p " +
            "WHERE p.seller.id IN :sellerIds AND p.status IN ('PENDING', 'APPROVED')")
    List<Object[]> findSiblingSummariesBySellerIds(@Param("sellerIds") Collection<Long> sellerIds);
}
