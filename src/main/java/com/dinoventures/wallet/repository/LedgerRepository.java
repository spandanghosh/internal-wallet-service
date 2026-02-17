package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.model.LedgerEntry;
import com.dinoventures.wallet.model.LedgerEntryView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class LedgerRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final RowMapper<LedgerEntry> ENTRY_ROW_MAPPER = (rs, rowNum) -> LedgerEntry.builder()
            .id(rs.getLong("id"))
            .transactionId(rs.getLong("transaction_id"))
            .walletId(rs.getLong("wallet_id"))
            .amount(rs.getLong("amount"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    private static final RowMapper<LedgerEntryView> VIEW_ROW_MAPPER = (rs, rowNum) -> LedgerEntryView.builder()
            .id(rs.getLong("id"))
            .transactionId(rs.getLong("transaction_id"))
            .transactionType(rs.getString("transaction_type"))
            .transactionDescription(rs.getString("transaction_description"))
            .walletId(rs.getLong("wallet_id"))
            .amount(rs.getLong("amount"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    /**
     * Inserts a single ledger entry. Must be called within a transaction.
     * Positive amount = credit (money flows in). Negative = debit (money flows out).
     */
    public void insert(long transactionId, long walletId, long amount) {
        namedJdbc.update(
                "INSERT INTO ledger_entries (transaction_id, wallet_id, amount) " +
                "VALUES (:transactionId, :walletId, :amount)",
                new MapSqlParameterSource(Map.of(
                        "transactionId", transactionId,
                        "walletId", walletId,
                        "amount", amount
                ))
        );
    }

    /**
     * Computes the current balance for a wallet.
     * balance = SUM(amount) from all ledger entries for that wallet.
     *
     * When called inside a transaction that holds a FOR UPDATE lock on the
     * wallet row, this read is serialized — no concurrent transaction can
     * insert new entries for this wallet until the lock is released.
     */
    public long getBalance(long accountId, long assetTypeId) {
        Long balance = namedJdbc.queryForObject(
                "SELECT COALESCE(SUM(le.amount), 0) " +
                "FROM ledger_entries le " +
                "JOIN wallets w ON w.id = le.wallet_id " +
                "WHERE w.account_id = :accountId AND w.asset_type_id = :assetTypeId",
                new MapSqlParameterSource(Map.of("accountId", accountId, "assetTypeId", assetTypeId)),
                Long.class
        );
        return balance != null ? balance : 0L;
    }

    /**
     * Returns all ledger entries for a given wallet, newest first, paginated.
     */
    public List<LedgerEntryView> getLedger(long accountId, long assetTypeId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return namedJdbc.query(
                "SELECT le.id, le.transaction_id, t.type AS transaction_type, " +
                "       t.description AS transaction_description, " +
                "       le.wallet_id, le.amount, le.created_at " +
                "FROM ledger_entries le " +
                "JOIN wallets w      ON w.id  = le.wallet_id " +
                "JOIN transactions t ON t.id  = le.transaction_id " +
                "WHERE w.account_id = :accountId AND w.asset_type_id = :assetTypeId " +
                "ORDER BY le.created_at DESC " +
                "LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource(Map.of(
                        "accountId", accountId,
                        "assetTypeId", assetTypeId,
                        "limit", pageSize,
                        "offset", offset
                )),
                VIEW_ROW_MAPPER
        );
    }

    /**
     * Total count of ledger entries for pagination metadata.
     */
    public long countLedger(long accountId, long assetTypeId) {
        Long count = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries le " +
                "JOIN wallets w ON w.id = le.wallet_id " +
                "WHERE w.account_id = :accountId AND w.asset_type_id = :assetTypeId",
                new MapSqlParameterSource(Map.of("accountId", accountId, "assetTypeId", assetTypeId)),
                Long.class
        );
        return count != null ? count : 0L;
    }

    /**
     * Returns all ledger entries for a specific transaction (used for idempotent replays).
     */
    public List<LedgerEntry> findByTransactionId(long transactionId) {
        return namedJdbc.query(
                "SELECT id, transaction_id, wallet_id, amount, created_at " +
                "FROM ledger_entries WHERE transaction_id = :transactionId ORDER BY id",
                new MapSqlParameterSource("transactionId", transactionId),
                ENTRY_ROW_MAPPER
        );
    }
}
