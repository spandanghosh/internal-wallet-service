package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.model.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WalletRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final RowMapper<Wallet> ROW_MAPPER = (rs, rowNum) -> Wallet.builder()
            .id(rs.getLong("id"))
            .accountId(rs.getLong("account_id"))
            .assetTypeId(rs.getLong("asset_type_id"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    /**
     * Idempotently get or create a wallet for the given (accountId, assetTypeId) pair.
     * Uses INSERT ON CONFLICT DO NOTHING so concurrent calls are safe.
     * Must be called within a transaction.
     */
    public Wallet getOrCreate(long accountId, long assetTypeId) {
        namedJdbc.update(
                "INSERT INTO wallets (account_id, asset_type_id) VALUES (:accountId, :assetTypeId) " +
                "ON CONFLICT (account_id, asset_type_id) DO NOTHING",
                new MapSqlParameterSource(Map.of("accountId", accountId, "assetTypeId", assetTypeId))
        );

        return namedJdbc.query(
                "SELECT id, account_id, asset_type_id, created_at FROM wallets " +
                "WHERE account_id = :accountId AND asset_type_id = :assetTypeId",
                new MapSqlParameterSource(Map.of("accountId", accountId, "assetTypeId", assetTypeId)),
                ROW_MAPPER
        ).stream().findFirst().orElseThrow(() ->
                new IllegalStateException("Wallet should exist after getOrCreate")
        );
    }

    /**
     * Acquires row-level locks on the specified wallets in ASCENDING ID ORDER.
     *
     * This is the deadlock prevention mechanism: by always locking in the same
     * consistent order (ascending wallet ID), we guarantee no two concurrent
     * transactions can ever form a circular wait. If Tx1 needs wallets {3,7}
     * and Tx2 needs wallets {7,3}, both will attempt to lock wallet 3 first,
     * and one will block — preventing a deadlock cycle.
     *
     * Must be called within a transaction.
     */
    public void lockForUpdate(List<Long> sortedWalletIds) {
        namedJdbc.query(
                "SELECT id FROM wallets WHERE id IN (:ids) ORDER BY id ASC FOR UPDATE",
                new MapSqlParameterSource("ids", sortedWalletIds),
                rs -> {}   // we only need the lock, discard result rows
        );
    }

    public Optional<Wallet> findById(long id) {
        List<Wallet> results = namedJdbc.query(
                "SELECT id, account_id, asset_type_id, created_at FROM wallets WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }
}
