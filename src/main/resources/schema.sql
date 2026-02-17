-- Auto-run by Spring Boot on startup (spring.sql.init.mode=always)
-- Safe to run multiple times: all statements use IF NOT EXISTS / ON CONFLICT DO NOTHING

CREATE TABLE IF NOT EXISTS asset_types (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    decimals    SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asset_types_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL    PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('user', 'system')),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS wallets (
    id             BIGSERIAL    PRIMARY KEY,
    account_id     BIGINT       NOT NULL REFERENCES accounts(id)    ON DELETE RESTRICT,
    asset_type_id  BIGINT       NOT NULL REFERENCES asset_types(id) ON DELETE RESTRICT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallets_account_asset UNIQUE (account_id, asset_type_id)
);

CREATE INDEX IF NOT EXISTS idx_wallets_account_id    ON wallets(account_id);
CREATE INDEX IF NOT EXISTS idx_wallets_asset_type_id ON wallets(asset_type_id);

CREATE TABLE IF NOT EXISTS transactions (
    id               BIGSERIAL    PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    type             VARCHAR(20)  NOT NULL CHECK (type IN ('topup', 'bonus', 'spend')),
    description      TEXT,
    metadata         JSONB,
    status           VARCHAR(20)  NOT NULL DEFAULT 'completed'
                         CHECK (status IN ('pending', 'completed', 'failed')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transactions_type            ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at      ON transactions(created_at DESC);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id              BIGSERIAL    PRIMARY KEY,
    transaction_id  BIGINT       NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    wallet_id       BIGINT       NOT NULL REFERENCES wallets(id)      ON DELETE RESTRICT,
    amount          BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ledger_entries_nonzero CHECK (amount <> 0)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_id     ON ledger_entries(wallet_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_amount ON ledger_entries(wallet_id, amount);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_time   ON ledger_entries(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_tx_id         ON ledger_entries(transaction_id);
