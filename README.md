# Idempotent Background Transaction Processing Service (Spring Boot)

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.2.3](https://img.shields.io/badge/Spring%20Boot-3.2.3-green.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()

Production-aware background transaction processing service designed to handle **out-of-order sequence arrival**, **duplicate request replay**, **transient failure retries**, and **strict financial idempotency**. Built with Spring Boot 3.2.3, Java 17, Spring Data JPA, and H2 database.

---

## 📌 Executive Summary & Architectural Core

In distributed enterprise environments, transactions often arrive out of order or as duplicates due to network retries, queue re-deliveries, or dynamic producer scheduling. This service guarantees **EXACTLY-ONCE** business execution semantics over **AT-LEAST-ONCE** delivery networks by combining:

1. **Dual-Layer Idempotency:** Fast-path `requestId` deduplication + entity-level `transactionId` lifecycle status checks.
2. **Option A Sequence-Based Buffering:** Holds premature sequence arrivals in `PENDING_SEQUENCE` status and triggers an automatic cascade flush when missing sequence prerequisites complete.
3. **Resilience & Transient Fault Recovery:** Handles transient database/lock timeouts via `RETRY_PENDING` exponential retry tracking.
4. **Operational Observability:** Aggregates status counters across `PROCESSED`, `DUPLICATE`, `PENDING_SEQUENCE`, and `FAILED` states.

---

## 📁 Repository Structure

```text
idempotent-transaction-service/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── enterprise/
    │               └── transaction/
    │                   ├── TransactionServiceApplication.java
    │                   ├── controller/
    │                   │   └── TransactionController.java
    │                   ├── dto/
    │                   │   ├── ProcessingSummary.java
    │                   │   ├── TransactionRequest.java
    │                   │   └── TransactionResponse.java
    │                   ├── entity/
    │                   │   ├── TransactionRecord.java
    │                   │   ├── TransactionStatus.java
    │                   │   └── TransactionType.java
    │                   ├── repository/
    │                   │   └── TransactionRepository.java
    │                   └── service/
    │                       └── TransactionProcessingService.java
    └── test/
        └── java/
            └── com/
                └── enterprise/
                    └── transaction/
                        └── TransactionProcessingServiceTest.java
```

---

## 🚀 Quick Start & Execution Instructions

### Prerequisites
* **Java Development Kit (JDK):** 17 or higher
* **Apache Maven:** 3.8+

### 1. Build and Run Automated Tests
```bash
mvn clean test
```

### 2. Run the Spring Boot Application
```bash
mvn spring-boot:run
```
The application will start on port `8080`.

---

## 🛠 API Usage & Ground Truth Testing

### Option 1: Batch Endpoint (`POST /api/v1/transactions/batch`)

In-order batch test execution demonstrating sequence buffering, contiguous cascade flushes, duplicate rejection, and transient failure recovery.

```bash
curl -X POST http://localhost:8080/api/v1/transactions/batch \
     -H "Content-Type: application/json" \
     -d '[
       {"transactionId":"TXN-2002","requestId":"REQ-2","sequenceNumber":2,"accountId":"ACC-201","transactionType":"DEBIT","amount":40,"currency":"USD"},
       {"transactionId":"TXN-2001","requestId":"REQ-1","sequenceNumber":1,"accountId":"ACC-201","transactionType":"CREDIT","amount":100,"currency":"USD"},
       {"transactionId":"TXN-2002","requestId":"REQ-2-DUP","sequenceNumber":2,"accountId":"ACC-201","transactionType":"DEBIT","amount":40,"currency":"USD"},
       {"transactionId":"TXN-2003","requestId":"REQ-3","sequenceNumber":3,"accountId":"ACC-201","transactionType":"CREDIT","amount":25,"currency":"USD"},
       {"transactionId":"TXN-2004","requestId":"REQ-4","sequenceNumber":4,"accountId":"ACC-202","transactionType":"DEBIT","amount":-10,"currency":"USD"},
       {"transactionId":"TXN-2005","requestId":"REQ-5","sequenceNumber":5,"accountId":"ACC-203","transactionType":"CREDIT","amount":500,"currency":"USD"}
     ]'
```

#### Expected Batch Output
```json
[
  {
    "transactionId": "TXN-2002",
    "requestId": "REQ-2",
    "accountId": "ACC-201",
    "status": "PENDING_SEQUENCE",
    "message": "Held in pending buffer waiting for seq 1"
  },
  {
    "transactionId": "TXN-2001",
    "requestId": "REQ-1",
    "accountId": "ACC-201",
    "status": "PROCESSED",
    "message": "Transaction processed successfully."
  },
  {
    "transactionId": "TXN-2002",
    "requestId": "REQ-2-DUP",
    "accountId": "ACC-201",
    "status": "DUPLICATE",
    "message": "Transaction already executed."
  },
  {
    "transactionId": "TXN-2003",
    "requestId": "REQ-3",
    "accountId": "ACC-201",
    "status": "PROCESSED",
    "message": "Transaction processed successfully."
  },
  {
    "transactionId": "TXN-2004",
    "requestId": "REQ-4",
    "accountId": "ACC-202",
    "status": "PENDING_SEQUENCE",
    "message": "Held in pending buffer waiting for seq 1"
  },
  {
    "transactionId": "TXN-2005",
    "requestId": "REQ-5",
    "accountId": "ACC-203",
    "status": "PROCESSED",
    "message": "Processed after 1 retry."
  }
]
```

---

### Option 2: Single Endpoint Status Scenarios (`POST /api/v1/transactions/single`)

#### 1. Scenario: `PROCESSED` (In-Order Sequence Execution)
```bash
curl -X POST http://localhost:8080/api/v1/transactions/single \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId": "TXN-2001",
       "requestId": "REQ-1",
       "accountId": "ACC-201",
       "transactionType": "CREDIT",
       "amount": 100.00,
       "currency": "USD",
       "sequenceNumber": 1
     }'
```
**Response:**
```json
{
  "transactionId": "TXN-2001",
  "requestId": "REQ-1",
  "accountId": "ACC-201",
  "status": "PROCESSED",
  "message": "Transaction processed successfully."
}
```

#### 2. Scenario: `PENDING_SEQUENCE` (Out-of-Order Buffer)
```bash
curl -X POST http://localhost:8080/api/v1/transactions/single \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId": "TXN-2002",
       "requestId": "REQ-2",
       "accountId": "ACC-201",
       "transactionType": "DEBIT",
       "amount": 40.00,
       "currency": "USD",
       "sequenceNumber": 2
     }'
```
**Response:**
```json
{
  "transactionId": "TXN-2002",
  "requestId": "REQ-2",
  "accountId": "ACC-201",
  "status": "PENDING_SEQUENCE",
  "message": "Held in pending buffer waiting for seq 1"
}
```

#### 3. Scenario: `DUPLICATE` (Re-Submitting Executed Payload)
```bash
curl -X POST http://localhost:8080/api/v1/transactions/single \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId": "TXN-2002",
       "requestId": "REQ-2-DUP",
       "accountId": "ACC-201",
       "transactionType": "DEBIT",
       "amount": 40.00,
       "currency": "USD",
       "sequenceNumber": 2
     }'
```
**Response:**
```json
{
  "transactionId": "TXN-2002",
  "requestId": "REQ-2-DUP",
  "accountId": "ACC-201",
  "status": "DUPLICATE",
  "message": "Transaction already executed."
}
```

#### 4. Scenario: `FAILED` (Bean Validation Error)
```bash
curl -X POST http://localhost:8080/api/v1/transactions/single \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId": "TXN-2004",
       "requestId": "REQ-4",
       "accountId": "ACC-202",
       "transactionType": "DEBIT",
       "amount": -10.00,
       "currency": "USD",
       "sequenceNumber": 4
     }'
```
**Response (HTTP 400 Bad Request):**
```json
{
  "timestamp": "2026-10-28T10:15:30.123+00:00",
  "status": 400,  "error": "Bad Request",
  "message": "Validation failed for object='transactionRequest'. Field error in 'amount': must be greater than or equal to 0.01",
  "path": "/api/v1/transactions/single"
}
```

---

### Option 3: Operational Summary Metrics (`GET /api/v1/transactions/summary`)

```bash
curl -X GET http://localhost:8080/api/v1/transactions/summary
```

**Response:**
```json
{
  "totalProcessed": 4,
  "totalDuplicates": 1,
  "totalPendingSequence": 1,
  "totalFailed": 0,
  "statusCounts": {
    "RECEIVED": 0,
    "PROCESSING": 0,
    "PROCESSED": 4,
    "PENDING_SEQUENCE": 1,
    "DUPLICATE": 1,
    "RETRY_PENDING": 0,
    "FAILED": 0
  }
}
```

---

## 💡 Production Hardening Roadmap

To scale this service to distributed high-throughput enterprise infrastructure:

* **Distributed Locking:** Implement **Redis Redlock** or **ShedLock** to prevent multi-node execution races per `accountId`.
* **Partitioned Queue Ingestion:** Stream requests via **Apache Kafka** or **AWS SQS FIFO** partitioned strictly by `accountId` to preserve ordering at the consumer level.
* **Database Isolation:** Enforce **PostgreSQL pessimistic locks (`SELECT FOR UPDATE`)** on sequence lookups to eliminate phantom reads during sequence gap resolution.