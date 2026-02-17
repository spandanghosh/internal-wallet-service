package com.dinoventures.wallet.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SpendRequest {

    @NotNull(message = "account_id is required")
    private Long accountId;

    @NotNull(message = "asset_type_id is required")
    private Long assetTypeId;

    @NotNull(message = "amount is required")
    @Min(value = 1, message = "amount must be at least 1")
    private Long amount;

    private String description;
}
