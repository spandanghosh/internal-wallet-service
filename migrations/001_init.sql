-- =============================================================================
-- Internal Wallet Service - Initial Schema
-- PostgreSQL 16+
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- asset_types: the virtual currency types (Gold Coins, Diamonds, etc.)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_types (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    decimals    SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asset_types_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- accounts: both user accounts and system accounts (Treasury, Revenue)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL    PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('user', 'system')),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- wallets: one wallet per (account, asset_type) pair
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    id             BIGSERIAL    PRIMARY KEY,
    account_id     BIGINT       NOT NULL REFERENCES accounts(id)    ON DELETE RESTRICT,
    asset_type_id  BIGINT       NOT NULL REFERENCES asset_types(id) ON DELETE RESTRICT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallets_account_asset UNIQUE (account_id, asset_type_id)
);

CREATE INDEX IF NOT EXISTS idx_wallets_account_id    ON wallets(account_id);
CREATE INDEX IF NOT EXISTS idx_wallets_asset_type_id ON wallets(asset_type_id);

-- ---------------------------------------------------------------------------
-- transactions: one record per logical financial operation.
-- idempotency_key UNIQUE constraint is the database-level guard against
-- duplicate processing — prevents double-spend and double-credit.
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- ledger_entries: the immutable source of truth for all balances.
--
-- Double-entry invariant: for every transaction, SUM(amount) across all
-- entries = 0. This means:
--   - positive amount = credit (money flows INTO the wallet)
--   - negative amount = debit  (money flows OUT of the wallet)
--
-- Balance for a wallet = SELECT COALESCE(SUM(amount), 0) FROM ledger_entries
--                        WHERE wallet_id = <id>
--
-- No stored balance column — eliminates balance drift bugs entirely.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entries (
    id              BIGSERIAL    PRIMARY KEY,
    transaction_id  BIGINT       NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    wallet_id       BIGINT       NOT NULL REFERENCES wallets(id)      ON DELETE RESTRICT,
    amount          BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ledger_entries_nonzero CHECK (amount <> 0)
);

-- Fast balance SUM per wallet
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_id     ON ledger_entries(wallet_id);
-- Covering index: wallet + amount for SUM queries
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_amount ON ledger_entries(wallet_id, amount);
-- Paginated ledger view per wallet, newest first
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_time   ON ledger_entries(wallet_id, created_at DESC);
-- Transaction lookup
CREATE INDEX IF NOT EXISTS idx_ledger_entries_tx_id         ON ledger_entries(transaction_id);

COMMIT;
