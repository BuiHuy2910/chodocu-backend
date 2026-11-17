package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(value = "SELECT t.id, o.id, p.title, b.fullName, s.fullName, " +
            "t.transactionAmount, t.platformFeeRate, t.platformFeeAmount, t.sellerReceiveAmount, t.createdAt " +
            "FROM Transaction t JOIN t.order o JOIN t.product p JOIN t.buyer b JOIN t.seller s " +
            "ORDER BY t.createdAt DESC",
            countQuery = "SELECT COUNT(t) FROM Transaction t")
    Page<Object[]> findTransactionRows(Pageable pageable);

    @Query("SELECT COUNT(t), COALESCE(SUM(t.transactionAmount), 0), COALESCE(SUM(t.platformFeeAmount), 0) FROM Transaction t")
    List<Object[]> getFinancialTotals();

    @Query("SELECT COALESCE(SUM(t.platformFeeAmount), 0) FROM Transaction t")
    BigDecimal sumTotalPlatformFees();

    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t")
    BigDecimal sumTotalTransactionValue();

    boolean existsByOrderId(Long orderId);
}
