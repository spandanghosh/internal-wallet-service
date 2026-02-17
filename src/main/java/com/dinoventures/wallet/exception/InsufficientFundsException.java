package com.dinoventures.wallet.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(long accountId, long assetTypeId, long available, long requested) {
        super(String.format(
            "Insufficient funds for account %d (asset_type %d): available=%d, requested=%d",
            accountId, assetTypeId, available, requested
        ));
    }
}
