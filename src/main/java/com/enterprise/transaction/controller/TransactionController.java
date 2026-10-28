package com.enterprise.transaction.controller;

import com.enterprise.transaction.dto.*;
import com.enterprise.transaction.service.TransactionProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionProcessingService service;

    @PostMapping("/single")
    public ResponseEntity<TransactionResponse> processSingle(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = service.processTransaction(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<TransactionResponse>> processBatch(@RequestBody List<TransactionRequest> requests) {
        List<TransactionResponse> responses = new ArrayList<>();
        for (TransactionRequest request : requests) {
            try {
                responses.add(service.processTransaction(request));
            } catch (Exception e) {
                responses.add(TransactionResponse.builder()
                        .transactionId(request.getTransactionId())
                        .requestId(request.getRequestId())
                        .accountId(request.getAccountId())
                        .status(com.enterprise.transaction.entity.TransactionStatus.FAILED)
                        .message("Validation/Processing Error: " + e.getMessage())
                        .build());
            }
        }
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/summary")
    public ResponseEntity<ProcessingSummary> getSummary() {
        return ResponseEntity.ok(service.getProcessingSummary());
    }
}
