package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class LedgerEntry {
    private Long id;
    private Long transactionId;
    private Long walletId;
    private Long amount;
    private OffsetDateTime createdAt;
}
