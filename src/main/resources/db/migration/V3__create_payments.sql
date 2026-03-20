-- V3: Payments
CREATE TABLE payments (
    id               BIGSERIAL      PRIMARY KEY,
    payment_ref      UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255)   UNIQUE,
    merchant_id      BIGINT         NOT NULL REFERENCES merchants(id),
    order_id         VARCHAR(100)   NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL DEFAULT 'NGN',
    channel          VARCHAR(20)    NOT NULL,  -- CARD|WALLET|BANK_TRANSFER
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    msc              NUMERIC(19, 2) NOT NULL DEFAULT 0,
    vat_amount       NUMERIC(19, 2) NOT NULL DEFAULT 0,
    processor_fee    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    processor_vat    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    payable_vat      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_payable   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    customer_id      VARCHAR(100),
    callback_url     VARCHAR(500),
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Indexes (heavy query fields)
CREATE INDEX idx_payments_merchant_id   ON payments(merchant_id);
CREATE INDEX idx_payments_status        ON payments(status);
CREATE INDEX idx_payments_channel       ON payments(channel);
CREATE INDEX idx_payments_created_at    ON payments(created_at);
CREATE INDEX idx_payments_order_id      ON payments(order_id);
CREATE INDEX idx_payments_idempotency   ON payments(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payments_merchant_status_date ON payments(merchant_id, status, created_at);
