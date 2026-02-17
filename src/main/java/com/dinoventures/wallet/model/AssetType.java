package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AssetType {
    private Long id;
    private String name;
    private String code;
    private Short decimals;
    private OffsetDateTime createdAt;
}
