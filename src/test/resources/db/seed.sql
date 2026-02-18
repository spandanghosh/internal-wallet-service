-- =============================================================================
-- Test seed data — plain INSERTs with predictable IDs.
-- Sequence is reset by truncate.sql before this runs, so IDs are deterministic.
--
-- Resulting IDs (referenced by test constants):
--   asset_types:  GOLD=1, DIAM=2, LPTS=3
--   accounts:     Treasury=1, Revenue=2, Alice=3, Bob=4
--   wallets:      Treasury/GOLD=1, Treasury/DIAM=2, Treasury/LPTS=3
--                 Revenue/GOLD=4,  Revenue/DIAM=5,  Revenue/LPTS=6
--                 Alice/GOLD=7,    Bob/GOLD=8
--   transactions: seed-alice=1, seed-bob=2
--   ledger_entries: 1..4
-- =============================================================================

-- Asset types
INSERT INTO asset_types (name, code, decimals) VALUES
    ('Gold Coins',     'GOLD', 0),
    ('Diamonds',       'DIAM', 0),
    ('Loyalty Points', 'LPTS', 0);

-- System accounts
INSERT INTO accounts (type, name) VALUES
    ('system', 'Treasury'),
    ('system', 'Revenue');

-- User accounts
INSERT INTO accounts (type, name) VALUES
    ('user', 'Alice'),
    ('user', 'Bob');

-- System wallets (all asset types)
INSERT INTO wallets (account_id, asset_type_id) VALUES
    (1, 1), (1, 2), (1, 3),   -- Treasury: GOLD, DIAM, LPTS
    (2, 1), (2, 2), (2, 3);   -- Revenue:  GOLD, DIAM, LPTS

-- User wallets (Gold Coins only)
INSERT INTO wallets (account_id, asset_type_id) VALUES
    (3, 1),   -- Alice / GOLD  (wallet id=7)
    (4, 1);   -- Bob   / GOLD  (wallet id=8)

-- Seed transactions
INSERT INTO transactions (idempotency_key, type, description, status) VALUES
    ('seed-alice-initial-gold', 'topup', 'Alice initial balance — 500 Gold Coins', 'completed'),
    ('seed-bob-initial-gold',   'topup', 'Bob initial balance — 200 Gold Coins',   'completed');

-- Alice: 500 Gold Coins  (Treasury wallet id=1, Alice wallet id=7)
INSERT INTO ledger_entries (transaction_id, wallet_id, amount) VALUES (1, 1, -500), (1, 7, 500);

-- Bob: 200 Gold Coins  (Treasury wallet id=1, Bob wallet id=8)
INSERT INTO ledger_entries (transaction_id, wallet_id, amount) VALUES (2, 1, -200), (2, 8, 200);
