package com.enterprise.transaction;

import com.enterprise.transaction.dto.TransactionRequest;
import com.enterprise.transaction.dto.TransactionResponse;
import com.enterprise.transaction.entity.TransactionStatus;
import com.enterprise.transaction.entity.TransactionType;
import com.enterprise.transaction.service.TransactionProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionProcessingServiceTest {

    @Autowired
    private TransactionProcessingService service;

    @Test
    void testGroundTruthDataSetExecution() {
        // 1. TXN-2002 (Seq 2) -> Out of order, should be PENDING_SEQUENCE
        TransactionRequest req1 = TransactionRequest.builder()
                .transactionId("TXN-2002").requestId("REQ-2").accountId("ACC-201")
                .transactionType(TransactionType.DEBIT).amount(new BigDecimal("40.00"))
                .currency("USD").sequenceNumber(2L).build();
        TransactionResponse res1 = service.processTransaction(req1);
        assertEquals(TransactionStatus.PENDING_SEQUENCE, res1.getStatus());

        // 2. TXN-2001 (Seq 1) -> In order, PROCESSED and flushes TXN-2002
        TransactionRequest req2 = TransactionRequest.builder()
                .transactionId("TXN-2001").requestId("REQ-1").accountId("ACC-201")
                .transactionType(TransactionType.CREDIT).amount(new BigDecimal("100.00"))
                .currency("USD").sequenceNumber(1L).build();
        TransactionResponse res2 = service.processTransaction(req2);
        assertEquals(TransactionStatus.PROCESSED, res2.getStatus());

        // 3. TXN-2002 Duplicate (REQ-2-DUP) -> Should be marked DUPLICATE
        TransactionRequest req3 = TransactionRequest.builder()
                .transactionId("TXN-2002").requestId("REQ-2-DUP").accountId("ACC-201")
                .transactionType(TransactionType.DEBIT).amount(new BigDecimal("40.00"))
                .currency("USD").sequenceNumber(2L).build();
        TransactionResponse res3 = service.processTransaction(req3);
        assertEquals(TransactionStatus.DUPLICATE, res3.getStatus());

        // 4. TXN-2003 (Seq 3) -> Next contiguous item, PROCESSED
        TransactionRequest req4 = TransactionRequest.builder()
                .transactionId("TXN-2003").requestId("REQ-3").accountId("ACC-201")
                .transactionType(TransactionType.CREDIT).amount(new BigDecimal("25.00"))
                .currency("USD").sequenceNumber(3L).build();
        TransactionResponse res4 = service.processTransaction(req4);
        assertEquals(TransactionStatus.PROCESSED, res4.getStatus());

        // 5. TXN-2005 (Seq 5) -> Transient failure simulation & retry
        TransactionRequest req6 = TransactionRequest.builder()
                .transactionId("TXN-2005").requestId("REQ-5").accountId("ACC-203")
                .transactionType(TransactionType.CREDIT).amount(new BigDecimal("500.00"))
                .currency("USD").sequenceNumber(5L).build();
        TransactionResponse res6 = service.processTransaction(req6);
        assertEquals(TransactionStatus.PROCESSED, res6.getStatus());
    }
}
