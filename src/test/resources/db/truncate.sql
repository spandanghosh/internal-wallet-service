-- Reset all tables and restart identity sequences.
-- Run before each test method to guarantee a clean, deterministic state.
TRUNCATE ledger_entries, transactions, wallets, accounts, asset_types
    RESTART IDENTITY CASCADE;
