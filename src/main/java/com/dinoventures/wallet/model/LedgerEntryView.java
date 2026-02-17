package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Denormalized view of a ledger entry including the transaction type,
 * returned by the /ledger endpoint for a rich audit trail.
 */
@Data
@Builder
public class LedgerEntryView {
    private Long id;
    private Long transactionId;
    private String transactionType;
    private String transactionDescription;
    private Long walletId;
    private Long amount;
    private OffsetDateTime createdAt;
}
