package com.chodocu.backend.repository;

import com.chodocu.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOrderByCreatedAtDesc();
    List<Transaction> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    List<Transaction> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    boolean existsByOrderId(Long orderId);

    @Query("SELECT COALESCE(SUM(t.platformFeeAmount), 0) FROM Transaction t")
    BigDecimal sumTotalPlatformFees();

    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t")
    BigDecimal sumTotalTransactionValue();
}
