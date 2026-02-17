package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class Transaction {
    private Long id;
    private String idempotencyKey;
    private String type;
    private String description;
    private String metadata;
    private String status;
    private OffsetDateTime createdAt;
}
