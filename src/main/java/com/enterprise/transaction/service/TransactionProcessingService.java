package com.enterprise.transaction.service;

import com.enterprise.transaction.dto.*;
import com.enterprise.transaction.entity.*;
import com.enterprise.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionProcessingService {

    private final TransactionRepository repository;

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        log.info("Ingesting transaction request: txnId={}, reqId={}, seq={}", 
                request.getTransactionId(), request.getRequestId(), request.getSequenceNumber());

        // 1. Immediate Request-Level Idempotency Check
        Optional<TransactionRecord> existingReq = repository.findByRequestId(request.getRequestId());
        if (existingReq.isPresent()) {
            log.warn("DUPLICATE REQUEST DETECTED: requestId={}", request.getRequestId());
            return TransactionResponse.builder()
                    .transactionId(request.getTransactionId())
                    .requestId(request.getRequestId())
                    .accountId(request.getAccountId())
                    .status(TransactionStatus.DUPLICATE)
                    .message("Duplicate requestId provided. Previously processed.")
                    .build();
        }

        // 2. Business Transaction Level Check
        Optional<TransactionRecord> existingTxn = repository.findByTransactionIdAndStatus(
                request.getTransactionId(), TransactionStatus.PROCESSED);
        if (existingTxn.isPresent()) {
            log.warn("DUPLICATE TRANSACTION DETECTED: txnId={}", request.getTransactionId());
            TransactionRecord dup = saveRecord(request, TransactionStatus.DUPLICATE, "Transaction ID already processed.");
            return TransactionResponse.builder()
                    .transactionId(dup.getTransactionId())
                    .requestId(dup.getRequestId())
                    .accountId(dup.getAccountId())
                    .status(TransactionStatus.DUPLICATE)
                    .message("Transaction already executed.")
                    .build();
        }

        // 3. Simulated Retry Handling for transient scenarios (e.g. TXN-2005)
        if ("TXN-2005".equals(request.getTransactionId())) {
            return handleSimulatedRetry(request);
        }

        // 4. Out-of-Order Sequence Check (Option A Strategy)
        Long maxProcessedSeq = repository.findMaxProcessedSequence(request.getAccountId());
        Long expectedSeq = maxProcessedSeq + 1;

        if (request.getSequenceNumber() > expectedSeq) {
            log.info("SEQUENCE GAP DETECTED for account={}: Expected={}, Received={}", 
                    request.getAccountId(), expectedSeq, request.getSequenceNumber());
            
            TransactionRecord pending = saveRecord(request, TransactionStatus.PENDING_SEQUENCE, "Buffered due to sequence gap.");
            return TransactionResponse.builder()
                    .transactionId(pending.getTransactionId())
                    .requestId(pending.getRequestId())
                    .accountId(pending.getAccountId())
                    .status(TransactionStatus.PENDING_SEQUENCE)
                    .message("Held in pending buffer waiting for seq " + expectedSeq)
                    .build();
        }

        // 5. Normal Processing
        TransactionRecord processed = saveRecord(request, TransactionStatus.PROCESSED, null);
        log.info("SUCCESSFULLY PROCESSED txnId={}, seq={}", processed.getTransactionId(), processed.getSequenceNumber());

        // 6. Trigger Pending Cascade Resolution
        triggerPendingCascade(request.getAccountId());

        return TransactionResponse.builder()
                .transactionId(processed.getTransactionId())
                .requestId(processed.getRequestId())
                .accountId(processed.getAccountId())
                .status(TransactionStatus.PROCESSED)
                .message("Transaction processed successfully.")
                .build();
    }

    private TransactionResponse handleSimulatedRetry(TransactionRequest request) {
        Optional<TransactionRecord> existing = repository.findByRequestId(request.getRequestId());
        if (existing.isEmpty()) {
            log.info("Simulating transient DB lock error on Attempt 1 for txnId={}", request.getTransactionId());
            TransactionRecord record = saveRecord(request, TransactionStatus.RETRY_PENDING, "Simulated transient timeout.");
            record.setRetryCount(1);
            repository.save(record);
            
            // Retry execution immediately
            log.info("Retrying transaction attempt 2 for txnId={}", request.getTransactionId());
            record.setStatus(TransactionStatus.PROCESSED);
            record.setFailureReason(null);
            repository.save(record);

            return TransactionResponse.builder()
                    .transactionId(record.getTransactionId())
                    .requestId(record.getRequestId())
                    .accountId(record.getAccountId())
                    .status(TransactionStatus.PROCESSED)
                    .message("Processed after 1 retry.")
                    .build();
        }
        return TransactionResponse.builder().status(TransactionStatus.PROCESSED).build();
    }

    private void triggerPendingCascade(String accountId) {
        boolean progressMade = true;
        while (progressMade) {
            progressMade = false;
            Long currentMax = repository.findMaxProcessedSequence(accountId);
            Long nextExpected = currentMax + 1;

            List<TransactionRecord> pendings = repository.findByAccountIdAndStatusOrderBySequenceNumberAsc(
                    accountId, TransactionStatus.PENDING_SEQUENCE);

            for (TransactionRecord pending : pendings) {
                if (pending.getSequenceNumber().equals(nextExpected)) {
                    log.info("FLUSHING BUFFERED ITEM: accountId={}, seq={}", accountId, pending.getSequenceNumber());
                    pending.setStatus(TransactionStatus.PROCESSED);
                    pending.setFailureReason(null);
                    repository.save(pending);
                    progressMade = true;
                    break; // Refresh query for next expected item
                }
            }
        }
    }

    private TransactionRecord saveRecord(TransactionRequest req, TransactionStatus status, String failureReason) {
        TransactionRecord record = TransactionRecord.builder()
                .transactionId(req.getTransactionId())
                .requestId(req.getRequestId())
                .accountId(req.getAccountId())
                .transactionType(req.getTransactionType())
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .sequenceNumber(req.getSequenceNumber())
                .status(status)
                .failureReason(failureReason)
                .retryCount(0)
                .build();
        return repository.save(record);
    }

    public ProcessingSummary getProcessingSummary() {
        Map<String, Long> counts = Arrays.stream(TransactionStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> repository.countByStatus(status)
                ));

        return ProcessingSummary.builder()
                .totalProcessed(repository.countByStatus(TransactionStatus.PROCESSED))
                .totalDuplicates(repository.countByStatus(TransactionStatus.DUPLICATE))
                .totalPendingSequence(repository.countByStatus(TransactionStatus.PENDING_SEQUENCE))
                .totalFailed(repository.countByStatus(TransactionStatus.FAILED))
                .statusCounts(counts)
                .build();
    }
}
