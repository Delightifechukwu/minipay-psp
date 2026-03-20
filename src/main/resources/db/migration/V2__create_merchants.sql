-- V2: Merchants and Charge Settings
CREATE TABLE merchants (
    id                  BIGSERIAL    PRIMARY KEY,
    merchant_id         VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    settlement_account  VARCHAR(20)  NOT NULL,
    settlement_bank     VARCHAR(100) NOT NULL,
    callback_url        VARCHAR(500),
    webhook_secret      VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE charge_settings (
    id                      BIGSERIAL      PRIMARY KEY,
    merchant_id             BIGINT         NOT NULL UNIQUE REFERENCES merchants(id) ON DELETE CASCADE,
    percentage_fee          NUMERIC(10, 6) NOT NULL DEFAULT 0,
    fixed_fee               NUMERIC(19, 2) NOT NULL DEFAULT 0,
    use_fixed_msc           BOOLEAN        NOT NULL DEFAULT FALSE,
    msc_cap                 NUMERIC(19, 2),
    vat_rate                NUMERIC(10, 6) NOT NULL DEFAULT 0,
    platform_provider_rate  NUMERIC(10, 6) NOT NULL DEFAULT 0,
    platform_provider_cap   NUMERIC(19, 2),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_merchants_merchant_id ON merchants(merchant_id);
CREATE INDEX idx_merchants_status      ON merchants(status);
CREATE INDEX idx_merchants_email       ON merchants(email);
CREATE INDEX idx_charge_settings_merchant ON charge_settings(merchant_id);
