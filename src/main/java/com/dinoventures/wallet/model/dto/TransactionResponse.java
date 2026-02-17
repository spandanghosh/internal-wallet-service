package com.dinoventures.wallet.model.dto;

import com.dinoventures.wallet.model.LedgerEntry;
import com.dinoventures.wallet.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TransactionResponse {
    private Transaction transaction;
    private List<LedgerEntry> ledgerEntries;
    /**
     * true if this response was replayed from a previously processed
     * request with the same Idempotency-Key (no new processing occurred).
     */
    private boolean idempotent;
}
