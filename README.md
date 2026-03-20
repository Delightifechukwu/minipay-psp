# MiniPay PSP — Payment Service Provider Backend

[![CI](https://github.com/Delightifechukwu/minipay-psp/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/minipay-psp/actions)
[![Coverage](.github/badges/coverage.svg)](target/site/jacoco/index.html)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)

> Production-ready mini payment service provider backend. Onboards merchants, processes card/wallet payments, computes fees/VAT with caps, triggers daily settlements, signs webhooks, and exports reports.

---

## Table of Contents

1. [Architecture](#architecture)
2. [ERD Snapshot](#erd-snapshot)
3. [Features](#features)
4. [Setup & Running](#setup--running)
5. [Environment Variables](#environment-variables)
6. [API Reference (cURL)](#api-reference-curl)
7. [Webhook Verification Guide](#webhook-verification-guide)
8. [Test Strategy](#test-strategy)
9. [Performance & Indexes](#performance--indexes)
10. [Decision Log](#decision-log)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        MiniPay PSP                               │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐   │
│  │   Auth   │  │Merchants │  │ Payments  │  │  Settlements │   │
│  │  (JWT)   │  │+Approval │  │+FeeCalc   │  │  (Scheduler) │   │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  └──────┬───────┘   │
│       │             │              │                │            │
│  ┌────▼─────────────▼──────────────▼────────────────▼───────┐   │
│  │                   PostgreSQL 16 (Flyway)                   │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │   Webhooks   │  │  Reporting   │  │   Rate Limiter       │   │
│  │  (Retry+DLQ) │  │  CSV / XLSX  │  │   (Bucket4j/Redis)   │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
         │                                       │
    ┌────▼─────┐                          ┌──────▼──────┐
    │  Redis   │                          │  Merchant   │
    │(RateLimit│                          │  Callbacks  │
    └──────────┘                          └─────────────┘
```

**Package structure** follows vertical slices:
```
com.minipay
├── config/          # Security, OpenAPI, JWT, RateLimit properties
├── auth/            # JWT auth, refresh tokens, RBAC
├── merchants/       # Merchant CRUD + MAKER/CHECKER approval
├── payments/        # Payment initiation, fee calc, idempotency
├── settlements/     # Daily batch aggregation, scheduled job
├── reporting/       # Paginated reports + CSV/XLSX export
├── webhooks/        # Event enqueue + retry scheduler (DLQ)
└── common/          # FeeCalculator, WebhookSig, errors, audit
```

---

## ERD Snapshot

```
users ──< user_roles >── roles
  │
  └── (authentication only)

merchants ──┤ charge_settings
    │
    └──< payments
           │
           └──< settlement_items >── settlement_batches
           │
           └──< webhook_events

approval_requests   (MAKER/CHECKER workflow)
audit_logs          (configuration change trail)
refresh_tokens      (rotated on each use)
```

Key relationships:
- One `Merchant` → one `ChargeSetting` (upsert semantics)
- One `Merchant` → many `Payments`
- One `SettlementBatch` → many `SettlementItems` → each linked to a `Payment`
- One `Payment` → zero or one `WebhookEvent` (enqueued after status change)

---

## Features

| Feature | Detail |
|---|---|
| **Auth** | JWT access + refresh tokens, BCrypt passwords, role-based (`ADMIN`, `MAKER`, `CHECKER`, `MERCHANT_USER`) |
| **MAKER/CHECKER** | MAKER submits merchant creation for approval; CHECKER approves/rejects; self-approval blocked |
| **Payments** | Fee/VAT/processor math with BigDecimal precision; pessimistic locking on status transitions |
| **Idempotency** | `Idempotency-Key` header; returns existing payment for PENDING; raises 409 for completed keys |
| **Rate Limiting** | Per-merchant token bucket (Bucket4j); per-IP login throttle (5 req/60s) |
| **Settlements** | Daily cron (01:00 WAT) + on-demand trigger; idempotent per merchant/period |
| **Webhooks** | HMAC-SHA256 signed; exponential backoff retry (×5); DLQ after max attempts |
| **Reports** | Paginated JSON + CSV + XLSX (Apache POI) for transactions and settlements |
| **Observability** | Correlation IDs on every request, structured logging, `/actuator/health` |

---

## Setup & Running

### Prerequisites
- Docker & Docker Compose, OR
- JDK 17+, Maven 3.9+, PostgreSQL 16, Redis 7

### Quick Start (Docker)

```bash
git clone https://github.com/YOUR_USERNAME/minipay-psp.git
cd minipay-psp

# Copy and configure environment
cp .env.example .env
# Edit .env — at minimum change JWT_SECRET

# Start everything
docker-compose up --build

# App runs at: http://localhost:8080
# Swagger UI:  http://localhost:8080/swagger-ui
```

### Local Development

```bash
# 1. Start infrastructure only
docker-compose up postgres redis -d

# 2. Run migrations (Flyway runs automatically on startup)
# Or manually:
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/minipay \
    -Dflyway.user=minipay -Dflyway.password=secret

# 3. Start application
mvn spring-boot:run

# 4. Open Swagger
open http://localhost:8080/swagger-ui
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/minipay` | PostgreSQL JDBC URL |
| `DB_USER` | `minipay` | Database username |
| `DB_PASS` | `secret` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | *(required)* | HMAC key ≥ 256 bits — **change in production** |
| `JWT_TTL_MIN` | `30` | Access token TTL (minutes) |
| `JWT_REFRESH_TTL_H` | `24` | Refresh token TTL (hours) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |

---

## API Reference (cURL)

### 1. Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq .

# Response: { "accessToken": "eyJ...", "refreshToken": "...", ... }
TOKEN="<accessToken from above>"
```

### 2. Create Merchant

```bash
curl -s -X POST http://localhost:8080/api/merchants \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Retail",
    "email": "acme@example.com",
    "settlementAccount": "0123456789",
    "settlementBank": "First Bank",
    "callbackUrl": "https://acme.example.com/webhook"
  }' | jq .

MERCHANT_ID="<merchantId from above>"
```

### 3. Configure Charge Settings

```bash
curl -s -X PUT "http://localhost:8080/api/merchants/$MERCHANT_ID/charge-settings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "percentageFee": 1.5,
    "fixedFee": 50,
    "useFixedMsc": false,
    "mscCap": 2000,
    "vatRate": 7.5,
    "platformProviderRate": 1.0,
    "platformProviderCap": 1200
  }' | jq .
```

### 4. Initiate Payment (with Idempotency Key)

```bash
curl -s -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{
    \"merchantId\": \"$MERCHANT_ID\",
    \"orderId\": \"ORDER-001\",
    \"amount\": 100000,
    \"currency\": \"NGN\",
    \"channel\": \"CARD\",
    \"customerId\": \"CUST-001\"
  }" | jq .

PAYMENT_REF="<paymentRef from above>"
```

### 5. Simulate Processor Callback (SUCCESS)

```bash
curl -s -X POST http://localhost:8080/api/simulate/processor-callback \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"paymentRef\": \"$PAYMENT_REF\", \"status\": \"SUCCESS\"}" | jq .
```

### 6. Generate Settlement

```bash
TODAY=$(date +%Y-%m-%d)
curl -s -X POST "http://localhost:8080/api/settlements/generate?from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 7. Export Transactions as XLSX

```bash
curl -s -X GET "http://localhost:8080/api/reports/transactions?format=XLSX" \
  -H "Authorization: Bearer $TOKEN" \
  --output transactions.xlsx
echo "Downloaded transactions.xlsx"
```

### 8. MAKER/CHECKER — Register MAKER user and submit for approval

```bash
# Admin registers a MAKER
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"maker1","email":"maker@test.com","password":"Maker@123","role":"MAKER"}'

# Login as MAKER
MAKER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"maker1","password":"Maker@123"}' | jq -r .accessToken)

# MAKER creates merchant — automatically submitted for approval
curl -s -X POST http://localhost:8080/api/merchants \
  -H "Authorization: Bearer $MAKER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Pending Merchant","email":"pending@test.com","settlementAccount":"9876543210","settlementBank":"GTB"}' | jq .

# ADMIN approves (get requestRef from the 202 response above)
REQUEST_REF="<requestRef from above>"
curl -s -X POST "http://localhost:8080/api/approvals/$REQUEST_REF/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note":"Verified and approved"}' | jq .
```

---

## Webhook Verification Guide

When a payment changes status, MiniPay POSTs to `merchant.callbackUrl` with:

```json
{
  "paymentRef": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "ORDER-001",
  "status": "SUCCESS",
  "amount": 100000,
  "currency": "NGN",
  "msc": 1550.00,
  "vatAmount": 116.25,
  "processorFee": 1000.00,
  "processorVat": 75.00,
  "amountPayable": 97258.75,
  "timestamp": "2025-09-09T12:00:00Z"
}
```

The `X-Signature` header contains `Base64(HMAC-SHA256(canonicalJson, webhookSecret))`.

### Verify in Java

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public boolean verifySignature(String payload, String secret, String receivedSig) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal(payload.getBytes()));
        // Constant-time comparison
        return MessageDigest.isEqual(expected.getBytes(), receivedSig.getBytes());
    } catch (Exception e) {
        return false;
    }
}
```

### Verify in Python

```python
import hmac, hashlib, base64

def verify_signature(payload: str, secret: str, received_sig: str) -> bool:
    expected = base64.b64encode(
        hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest()
    ).decode()
    return hmac.compare_digest(expected, received_sig)
```

### Verify in Node.js

```javascript
const crypto = require('crypto');

function verifySignature(payload, secret, receivedSig) {
  const expected = crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('base64');
  return crypto.timingSafeEqual(
    Buffer.from(expected),
    Buffer.from(receivedSig)
  );
}
```

> **Important:** Always use constant-time comparison (`timingSafeEqual`, `hmac.compare_digest`, `MessageDigest.isEqual`) to prevent timing attacks.

---

## Test Strategy

```
src/test/
├── common/
│   ├── FeeCalculatorTest.java         # 15 unit tests — all spec examples + edge cases
│   └── WebhookSignatureUtilTest.java   # 8 unit tests — determinism, tamper, timing
├── merchants/
│   └── MerchantRepositoryTest.java    # JPA slice tests with Testcontainers
└── payments/
    └── PaymentFlowIntegrationTest.java # 11 ordered E2E tests:
        # Login → Merchant → ChargeSettings → Pay → Idempotency
        # → Callback → Verify → Double-transition guard
        # → Settlement → Report → CSV export
```

### Running Tests

```bash
# All tests (requires Docker for Testcontainers)
mvn verify

# Unit tests only (no Docker needed)
mvn test -Dtest="FeeCalculatorTest,WebhookSignatureUtilTest"

# Integration tests only
mvn verify -Dtest="PaymentFlowIntegrationTest,MerchantRepositoryTest"

# Coverage report
mvn verify
open target/site/jacoco/index.html
```

---

## Performance & Indexes

### Database Indexes Added (V3, V4, V5 migrations)

| Table | Index | Purpose |
|---|---|---|
| `payments` | `(merchant_id, status, created_at)` | Settlement aggregation query |
| `payments` | `(idempotency_key)` WHERE NOT NULL | Fast idempotency lookup |
| `payments` | `(status)`, `(channel)`, `(created_at)` | Report filter columns |
| `settlement_batches` | `(merchant_id)`, `(period_start, period_end)` | Filter + idempotency check |
| `webhook_events` | `(next_retry_at)` WHERE `status='PENDING'` | Partial index — retry scheduler |
| `merchants` | `(merchant_id)`, `(email)`, `(status)` | Lookup + dedup |
| `audit_logs` | `(entity_type, entity_id)`, `(created_at)` | Audit trail queries |

### N+1 Prevention
- `findByUsernameWithRoles` — JOIN FETCH to avoid lazy-load on every auth check
- `findByMerchantIdWithChargeSettings` — eager fetch for payment fee computation
- `findBySettlementRefWithItems` — JOIN FETCH on batch detail endpoint
- Settlement aggregation uses a direct `List<Payment>` query — no per-payment selects

### Pagination
All list endpoints are paginated. Default page size: 20. Max enforced at query level.
Export endpoints bypass pagination intentionally (file download use case).

---

## Decision Log

| Decision | Choice | Rationale |
|---|---|---|
| **payableVat can be negative** | Allowed, documented | Spec says "can be negative — handle/document". When processorVat > vatAmount, MiniPay has a VAT credit. Stored at scale=4 to preserve sub-cent precision. |
| **processorVat rate** | Same `vatRate` as merchant | Spec gives "or a separate rate; document your choice". Using same rate simplifies configuration while remaining correct. |
| **Webhook transaction isolation** | `REQUIRES_NEW` | Webhook enqueue runs in its own transaction so a payment-service rollback does not suppress the webhook record. |
| **Rate limiter backing** | In-memory Bucket4j | Sufficient for single-instance. Swap to Redis-backed Bucket4j for multi-instance (commented in code). |
| **Idempotency for PENDING** | Returns 200 silently | PENDING payment with same key = safe retry in progress. Returns current state without 409. |
| **MAKER/CHECKER scope** | Merchant creation only | Extending to charge settings left as stretch goal — approval table is generic enough to support it. |
| **Settlement cron timezone** | `Africa/Lagos` (WAT) | Settlements should settle the previous local business day, not UTC midnight. |
| **BigDecimal scale** | Currency=2, rates=6, payableVat=4 | Rates like 1.5% need 6dp precision. payableVat=4 avoids rounding errors in aggregation. |
| **Password hash strength** | BCrypt strength=12 | Balances security and login latency (~250ms on modern hardware). |

### What I'd Improve Next
1. **Redis-backed rate limiter** — replace in-memory Bucket4j for horizontal scaling
2. **Async webhook delivery** — use Spring `@Async` or a virtual-thread executor instead of synchronous HTTP in the scheduler
3. **MAKER/CHECKER on charge settings** — extend approval workflow beyond merchant creation
4. **Merchant webhook secret rotation** — endpoint to rotate `webhookSecret` without breaking existing deliveries
5. **Kafka integration** — replace scheduler-based webhook retry with Kafka topics + DLQ for guaranteed delivery
6. **Distributed tracing** — integrate Micrometer Tracing + Zipkin/OTLP
