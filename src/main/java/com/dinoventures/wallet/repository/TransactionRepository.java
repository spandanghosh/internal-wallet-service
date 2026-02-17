package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, rowNum) -> Transaction.builder()
            .id(rs.getLong("id"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .type(rs.getString("type"))
            .description(rs.getString("description"))
            .metadata(rs.getString("metadata"))
            .status(rs.getString("status"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    /**
     * Attempts to insert a new transaction row.
     *
     * Uses ON CONFLICT DO NOTHING on the unique idempotency_key column.
     * Returns the number of rows inserted:
     *   1 = new transaction (proceed with business logic)
     *   0 = duplicate key (return cached result to caller)
     *
     * Must be called within a transaction.
     */
    public int insertIfNew(String idempotencyKey, String type, String description) {
        return namedJdbc.update(
                "INSERT INTO transactions (idempotency_key, type, description, status) " +
                "VALUES (:key, :type, :description, 'completed') " +
                "ON CONFLICT (idempotency_key) DO NOTHING",
                new MapSqlParameterSource(Map.of(
                        "key", idempotencyKey,
                        "type", type,
                        "description", description != null ? description : ""
                ))
        );
    }

    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        List<Transaction> results = namedJdbc.query(
                "SELECT id, idempotency_key, type, description, metadata, status, created_at " +
                "FROM transactions WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", idempotencyKey),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }

    public Optional<Transaction> findById(long id) {
        List<Transaction> results = namedJdbc.query(
                "SELECT id, idempotency_key, type, description, metadata, status, created_at " +
                "FROM transactions WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }
}
