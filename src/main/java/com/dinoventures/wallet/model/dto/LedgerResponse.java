package com.dinoventures.wallet.model.dto;

import com.dinoventures.wallet.model.LedgerEntryView;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LedgerResponse {
    private List<LedgerEntryView> entries;
    private long total;
    private int page;
    private int pageSize;
}
