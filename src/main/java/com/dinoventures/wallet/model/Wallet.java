package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class Wallet {
    private Long id;
    private Long accountId;
    private Long assetTypeId;
    private OffsetDateTime createdAt;
}
