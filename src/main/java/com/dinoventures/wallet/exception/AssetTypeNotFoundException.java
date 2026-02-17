package com.dinoventures.wallet.exception;

public class AssetTypeNotFoundException extends RuntimeException {
    public AssetTypeNotFoundException(long id) {
        super("Asset type not found: id=" + id);
    }
}
