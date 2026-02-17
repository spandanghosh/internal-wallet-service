-- =============================================================================
-- Internal Wallet Service - Seed Data
-- Run AFTER migrations/001_init.sql
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- Asset Types
-- ---------------------------------------------------------------------------
INSERT INTO asset_types (name, code, decimals) VALUES
    ('Gold Coins',      'GOLD', 0),
    ('Diamonds',        'DIAM', 0),
    ('Loyalty Points',  'LPTS', 0)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- System Accounts
--   Treasury: source of all top-ups and bonus credits
--   Revenue:  sink that receives all user spends
-- ---------------------------------------------------------------------------
INSERT INTO accounts (type, name) VALUES
    ('system', 'Treasury'),
    ('system', 'Revenue')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- User Accounts
-- ---------------------------------------------------------------------------
INSERT INTO accounts (type, name) VALUES
    ('user', 'Alice'),
    ('user', 'Bob')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Wallets
-- System accounts (Treasury, Revenue) get wallets for ALL asset types.
-- User accounts get wallets for Gold Coins initially.
-- ---------------------------------------------------------------------------

-- System wallets for every asset type
INSERT INTO wallets (account_id, asset_type_id)
SELECT a.id, at.id
FROM accounts a, asset_types at
WHERE a.type = 'system'
ON CONFLICT (account_id, asset_type_id) DO NOTHING;

-- User wallets for Gold Coins
INSERT INTO wallets (account_id, asset_type_id)
SELECT a.id, at.id
FROM accounts a, asset_types at
WHERE a.name IN ('Alice', 'Bob') AND at.code = 'GOLD'
ON CONFLICT (account_id, asset_type_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Initial Balances (seeded as real ledger entries — double-entry)
--
-- Alice: 500 Gold Coins
-- Bob:   200 Gold Coins
--
-- Treasury is debited (negative), Users are credited (positive).
-- Each seed uses a deterministic idempotency key for re-runnability.
-- ---------------------------------------------------------------------------

-- Alice initial balance
INSERT INTO transactions (idempotency_key, type, description, status)
VALUES ('seed-alice-initial-gold', 'topup', 'Initial seed balance for Alice — 500 Gold Coins', 'completed')
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO ledger_entries (transaction_id, wallet_id, amount)
SELECT
    t.id,
    w.id,
    -500
FROM transactions t, wallets w
JOIN accounts a     ON a.id  = w.account_id
JOIN asset_types at ON at.id = w.asset_type_id
WHERE t.idempotency_key = 'seed-alice-initial-gold'
  AND a.name = 'Treasury'
  AND at.code = 'GOLD'
  AND NOT EXISTS (
      SELECT 1 FROM ledger_entries le WHERE le.transaction_id = t.id AND le.wallet_id = w.id
  );

INSERT INTO ledger_entries (transaction_id, wallet_id, amount)
SELECT
    t.id,
    w.id,
    500
FROM transactions t, wallets w
JOIN accounts a     ON a.id  = w.account_id
JOIN asset_types at ON at.id = w.asset_type_id
WHERE t.idempotency_key = 'seed-alice-initial-gold'
  AND a.name = 'Alice'
  AND at.code = 'GOLD'
  AND NOT EXISTS (
      SELECT 1 FROM ledger_entries le WHERE le.transaction_id = t.id AND le.wallet_id = w.id
  );

-- Bob initial balance
INSERT INTO transactions (idempotency_key, type, description, status)
VALUES ('seed-bob-initial-gold', 'topup', 'Initial seed balance for Bob — 200 Gold Coins', 'completed')
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO ledger_entries (transaction_id, wallet_id, amount)
SELECT
    t.id,
    w.id,
    -200
FROM transactions t, wallets w
JOIN accounts a     ON a.id  = w.account_id
JOIN asset_types at ON at.id = w.asset_type_id
WHERE t.idempotency_key = 'seed-bob-initial-gold'
  AND a.name = 'Treasury'
  AND at.code = 'GOLD'
  AND NOT EXISTS (
      SELECT 1 FROM ledger_entries le WHERE le.transaction_id = t.id AND le.wallet_id = w.id
  );

INSERT INTO ledger_entries (transaction_id, wallet_id, amount)
SELECT
    t.id,
    w.id,
    200
FROM transactions t, wallets w
JOIN accounts a     ON a.id  = w.account_id
JOIN asset_types at ON at.id = w.asset_type_id
WHERE t.idempotency_key = 'seed-bob-initial-gold'
  AND a.name = 'Bob'
  AND at.code = 'GOLD'
  AND NOT EXISTS (
      SELECT 1 FROM ledger_entries le WHERE le.transaction_id = t.id AND le.wallet_id = w.id
  );

COMMIT;
