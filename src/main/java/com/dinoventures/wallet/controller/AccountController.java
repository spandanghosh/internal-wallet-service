package com.dinoventures.wallet.controller;

import com.dinoventures.wallet.model.Account;
import com.dinoventures.wallet.model.AssetType;
import com.dinoventures.wallet.model.dto.*;
import com.dinoventures.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final WalletService walletService;

    /**
     * GET /health
     * Liveness probe — returns 200 if the service is running.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * GET /api/v1/asset-types
     * Lists all configured asset types (Gold Coins, Diamonds, etc.)
     */
    @GetMapping("/api/v1/asset-types")
    public ResponseEntity<List<AssetType>> listAssetTypes() {
        return ResponseEntity.ok(walletService.getAllAssetTypes());
    }

    /**
     * GET /api/v1/accounts
     * Lists all accounts (users and system accounts).
     */
    @GetMapping("/api/v1/accounts")
    public ResponseEntity<List<Account>> listAccounts() {
        return ResponseEntity.ok(walletService.getAllAccounts());
    }

    /**
     * POST /api/v1/accounts
     * Creates a new account (user or system).
     */
    @PostMapping("/api/v1/accounts")
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        Account account = walletService.createAccount(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    /**
     * GET /api/v1/accounts/{id}/balance?asset_type_id=1
     * Returns the computed balance for a specific account and asset type.
     * Balance is always derived from SUM(ledger_entries.amount) — never cached.
     */
    @GetMapping("/api/v1/accounts/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable("id") long accountId,
            @RequestParam("asset_type_id") long assetTypeId) {

        BalanceResponse response = walletService.getBalance(accountId, assetTypeId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/accounts/{id}/ledger?asset_type_id=1&page=1&page_size=20
     * Returns the paginated ledger history for an account + asset type.
     * Entries are returned newest-first.
     */
    @GetMapping("/api/v1/accounts/{id}/ledger")
    public ResponseEntity<LedgerResponse> getLedger(
            @PathVariable("id") long accountId,
            @RequestParam("asset_type_id") long assetTypeId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        LedgerResponse response = walletService.getLedger(accountId, assetTypeId, page, pageSize);
        return ResponseEntity.ok(response);
    }
}
