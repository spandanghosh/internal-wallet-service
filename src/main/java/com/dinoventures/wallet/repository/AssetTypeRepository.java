package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.model.AssetType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AssetTypeRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final RowMapper<AssetType> ROW_MAPPER = (rs, rowNum) -> AssetType.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .code(rs.getString("code"))
            .decimals(rs.getShort("decimals"))
            .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .build();

    public List<AssetType> findAll() {
        return namedJdbc.query(
                "SELECT id, name, code, decimals, created_at FROM asset_types ORDER BY id",
                ROW_MAPPER
        );
    }

    public Optional<AssetType> findById(long id) {
        List<AssetType> results = namedJdbc.query(
                "SELECT id, name, code, decimals, created_at FROM asset_types WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return results.stream().findFirst();
    }
}
