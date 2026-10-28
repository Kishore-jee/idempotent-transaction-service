package com.enterprise.transaction.dto;

import com.enterprise.transaction.entity.TransactionStatus;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponse {
    private String transactionId;
    private String requestId;
    private String accountId;
    private TransactionStatus status;
    private String message;
}
