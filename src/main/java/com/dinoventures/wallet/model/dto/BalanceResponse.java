package com.dinoventures.wallet.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private Long accountId;
    private Long assetTypeId;
    private Long balance;
}
