package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.model.Account;
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
public class AccountRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final RowMapper<Account> ROW_MAPPER = (rs, rowNum) -> Account.builder()
            .id(rs.getLong("id"))
            .type(rs.getString("type"))
            .name(rs.getString("name"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    public List<Account> findAll() {
        return namedJdbc.query(
                "SELECT id, type, name, created_at FROM accounts ORDER BY id",
                ROW_MAPPER
        );
    }

    public Optional<Account> findById(long id) {
        List<Account> results = namedJdbc.query(
                "SELECT id, type, name, created_at FROM accounts WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }

    public Optional<Account> findByName(String name) {
        List<Account> results = namedJdbc.query(
                "SELECT id, type, name, created_at FROM accounts WHERE name = :name",
                new MapSqlParameterSource("name", name),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }

    public Account save(String type, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(
                "INSERT INTO accounts (type, name) VALUES (:type, :name)",
                new MapSqlParameterSource(Map.of("type", type, "name", name)),
                keyHolder,
                new String[]{"id"}
        );
        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }
}
