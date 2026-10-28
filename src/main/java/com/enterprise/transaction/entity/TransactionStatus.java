package com.enterprise.transaction.entity;

public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    PENDING_SEQUENCE,
    DUPLICATE,
    RETRY_PENDING,
    FAILED
}
