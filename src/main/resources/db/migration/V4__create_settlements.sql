-- V4: Settlement Batches and Items
CREATE TABLE settlement_batches (
    id                  BIGSERIAL      PRIMARY KEY,
    settlement_ref      UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    merchant_id         BIGINT         NOT NULL REFERENCES merchants(id),
    period_start        DATE           NOT NULL,
    period_end          DATE           NOT NULL,
    count               INTEGER        NOT NULL DEFAULT 0,
    transaction_amount  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    msc                 NUMERIC(19, 2) NOT NULL DEFAULT 0,
    vat_amount          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    processor_fee       NUMERIC(19, 2) NOT NULL DEFAULT 0,
    processor_vat       NUMERIC(19, 2) NOT NULL DEFAULT 0,
    income              NUMERIC(19, 2) NOT NULL DEFAULT 0,
    payable_vat         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_payable      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, period_start, period_end)
);

CREATE TABLE settlement_items (
    id              BIGSERIAL      PRIMARY KEY,
    batch_id        BIGINT         NOT NULL REFERENCES settlement_batches(id) ON DELETE CASCADE,
    payment_id      BIGINT         NOT NULL REFERENCES payments(id),
    amount          NUMERIC(19, 2) NOT NULL,
    msc             NUMERIC(19, 2) NOT NULL,
    vat_amount      NUMERIC(19, 2) NOT NULL,
    processor_fee   NUMERIC(19, 2) NOT NULL,
    processor_vat   NUMERIC(19, 2) NOT NULL,
    amount_payable  NUMERIC(19, 2) NOT NULL,
    UNIQUE (batch_id, payment_id)
);

CREATE INDEX idx_settlement_batches_merchant    ON settlement_batches(merchant_id);
CREATE INDEX idx_settlement_batches_period      ON settlement_batches(period_start, period_end);
CREATE INDEX idx_settlement_batches_status      ON settlement_batches(status);
CREATE INDEX idx_settlement_batches_created     ON settlement_batches(created_at);
CREATE INDEX idx_settlement_items_batch         ON settlement_items(batch_id);
CREATE INDEX idx_settlement_items_payment       ON settlement_items(payment_id);
