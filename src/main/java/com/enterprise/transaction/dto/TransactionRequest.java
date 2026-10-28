package com.enterprise.transaction.dto;

import com.enterprise.transaction.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotBlank(message = "requestId is required")
    private String requestId;

    @NotBlank(message = "accountId is required")
    private String accountId;

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be strictly positive")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotNull(message = "sequenceNumber is required")
    @Min(value = 1, message = "sequenceNumber must be >= 1")
    private Long sequenceNumber;
}
