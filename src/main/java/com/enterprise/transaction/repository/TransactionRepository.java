package com.enterprise.transaction.repository;

import com.enterprise.transaction.entity.TransactionRecord;
import com.enterprise.transaction.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByRequestId(String requestId);

    Optional<TransactionRecord> findByTransactionIdAndStatus(String transactionId, TransactionStatus status);

    @Query("SELECT COALESCE(MAX(t.sequenceNumber), 0) FROM TransactionRecord t WHERE t.accountId = :accountId AND t.status = 'PROCESSED'")
    Long findMaxProcessedSequence(@Param("accountId") String accountId);

    List<TransactionRecord> findByAccountIdAndStatusOrderBySequenceNumberAsc(String accountId, TransactionStatus status);

    long countByStatus(TransactionStatus status);
}
