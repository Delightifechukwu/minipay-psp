-- V5: Webhook Events (with retry DLQ) + Audit Log
CREATE TABLE webhook_events (
    id              BIGSERIAL      PRIMARY KEY,
    merchant_id     BIGINT         NOT NULL REFERENCES merchants(id),
    payment_id      BIGINT         NOT NULL REFERENCES payments(id),
    event_type      VARCHAR(50)    NOT NULL DEFAULT 'PAYMENT_STATUS_CHANGED',
    payload         TEXT           NOT NULL,
    target_url      VARCHAR(500)   NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING|SUCCESS|FAILED|DLQ
    attempt_count   INTEGER        NOT NULL DEFAULT 0,
    max_attempts    INTEGER        NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_status       ON webhook_events(status);
CREATE INDEX idx_webhook_events_next_retry   ON webhook_events(next_retry_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_events_merchant     ON webhook_events(merchant_id);
CREATE INDEX idx_webhook_events_payment      ON webhook_events(payment_id);

-- Audit log for MAKER/CHECKER workflow
CREATE TABLE audit_logs (
    id              BIGSERIAL    PRIMARY KEY,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    actor_username  VARCHAR(100) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    correlation_id  VARCHAR(36),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor   ON audit_logs(actor_username);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);

-- Maker/Checker approval queue
CREATE TABLE approval_requests (
    id              BIGSERIAL    PRIMARY KEY,
    request_ref     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(100),
    action          VARCHAR(50)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|REJECTED
    maker_username  VARCHAR(100) NOT NULL,
    checker_username VARCHAR(100),
    checker_note    VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_maker  ON approval_requests(maker_username);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expiry  ON refresh_tokens(expires_at) WHERE NOT revoked;
