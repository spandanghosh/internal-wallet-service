package com.dinoventures.wallet.service;

import com.dinoventures.wallet.exception.AccountNotFoundException;
import com.dinoventures.wallet.exception.AssetTypeNotFoundException;
import com.dinoventures.wallet.exception.InsufficientFundsException;
import com.dinoventures.wallet.model.Account;
import com.dinoventures.wallet.model.LedgerEntryView;
import com.dinoventures.wallet.model.Transaction;
import com.dinoventures.wallet.model.Wallet;
import com.dinoventures.wallet.model.dto.*;
import com.dinoventures.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WalletService {

    // System account names — must match the seed data
    private static final String TREASURY_NAME = "Treasury";
    private static final String REVENUE_NAME  = "Revenue";

    private final AccountRepository    accountRepo;
    private final AssetTypeRepository  assetTypeRepo;
    private final WalletRepository     walletRepo;
    private final TransactionRepository txRepo;
    private final LedgerRepository     ledgerRepo;

    // =========================================================================
    // TRANSACTION FLOWS
    // =========================================================================

    /**
     * Wallet Top-up (Purchase): credits a user's wallet.
     *
     * Flow: Treasury wallet -amount, User wallet +amount
     *
     * Algorithm (inside a single DB transaction):
     *   1. Idempotency gate: INSERT transaction ON CONFLICT DO NOTHING
     *   2. If duplicate key → return cached result (idempotent=true)
     *   3. Resolve/create wallets for Treasury and User
     *   4. Lock wallets in ascending ID order (deadlock prevention)
     *   5. Insert two balanced ledger entries (Treasury −N, User +N)
     *   6. Commit
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse topup(TopupRequest req, String idempotencyKey) {
        validateAccountExists(req.getAccountId());
        validateAssetTypeExists(req.getAssetTypeId());

        // Step 1+2: Idempotency gate
        int rowsInserted = txRepo.insertIfNew(idempotencyKey, "topup",
                req.getDescription() != null ? req.getDescription() : "Wallet top-up");
        Transaction txn = txRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();

        if (rowsInserted == 0) {
            // Duplicate request — return the cached result
            return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), true);
        }

        // Step 3: Resolve system treasury and user wallets
        Account treasury = accountRepo.findByName(TREASURY_NAME)
                .orElseThrow(() -> new AccountNotFoundException(TREASURY_NAME));
        Wallet treasuryWallet = walletRepo.getOrCreate(treasury.getId(), req.getAssetTypeId());
        Wallet userWallet     = walletRepo.getOrCreate(req.getAccountId(), req.getAssetTypeId());

        // Step 4: Lock wallets in ascending ID order — prevents deadlocks
        List<Long> sortedIds = Stream.of(treasuryWallet.getId(), userWallet.getId())
                .sorted()
                .toList();
        walletRepo.lockForUpdate(sortedIds);

        // Step 5: Double-entry ledger (SUM = 0, balanced)
        ledgerRepo.insert(txn.getId(), treasuryWallet.getId(), -req.getAmount());  // Treasury debited
        ledgerRepo.insert(txn.getId(), userWallet.getId(),     +req.getAmount());  // User credited

        return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), false);
    }

    /**
     * Bonus/Incentive: issues free credits from Treasury to a user.
     *
     * Structurally identical to top-up but recorded as type="bonus" for
     * reporting and analytics differentiation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse bonus(BonusRequest req, String idempotencyKey) {
        validateAccountExists(req.getAccountId());
        validateAssetTypeExists(req.getAssetTypeId());

        int rowsInserted = txRepo.insertIfNew(idempotencyKey, "bonus",
                req.getDescription() != null ? req.getDescription() : "Bonus credit");
        Transaction txn = txRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();

        if (rowsInserted == 0) {
            return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), true);
        }

        Account treasury = accountRepo.findByName(TREASURY_NAME)
                .orElseThrow(() -> new AccountNotFoundException(TREASURY_NAME));
        Wallet treasuryWallet = walletRepo.getOrCreate(treasury.getId(), req.getAssetTypeId());
        Wallet userWallet     = walletRepo.getOrCreate(req.getAccountId(), req.getAssetTypeId());

        List<Long> sortedIds = Stream.of(treasuryWallet.getId(), userWallet.getId())
                .sorted()
                .toList();
        walletRepo.lockForUpdate(sortedIds);

        ledgerRepo.insert(txn.getId(), treasuryWallet.getId(), -req.getAmount());
        ledgerRepo.insert(txn.getId(), userWallet.getId(),     +req.getAmount());

        return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), false);
    }

    /**
     * Purchase/Spend: debits a user's wallet and credits the Revenue account.
     *
     * Critical safety: balance is checked INSIDE the locked transaction.
     * The FOR UPDATE lock ensures no concurrent transaction can modify
     * the user's wallet between our balance read and ledger insert.
     *
     * Algorithm:
     *   1. Idempotency gate
     *   2. If duplicate → return cached result
     *   3. Resolve/create wallets for User and Revenue
     *   4. Lock wallets in ascending ID order
     *   5. Compute user balance INSIDE the lock (serialized read)
     *   6. If balance < amount → throw InsufficientFundsException (triggers rollback)
     *   7. Insert two balanced ledger entries (User −N, Revenue +N)
     *   8. Commit
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse spend(SpendRequest req, String idempotencyKey) {
        validateAccountExists(req.getAccountId());
        validateAssetTypeExists(req.getAssetTypeId());

        // Step 1+2: Idempotency gate
        int rowsInserted = txRepo.insertIfNew(idempotencyKey, "spend",
                req.getDescription() != null ? req.getDescription() : "Credit spend");
        Transaction txn = txRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();

        if (rowsInserted == 0) {
            return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), true);
        }

        // Step 3: Resolve wallets
        Account revenue = accountRepo.findByName(REVENUE_NAME)
                .orElseThrow(() -> new AccountNotFoundException(REVENUE_NAME));
        Wallet userWallet    = walletRepo.getOrCreate(req.getAccountId(), req.getAssetTypeId());
        Wallet revenueWallet = walletRepo.getOrCreate(revenue.getId(), req.getAssetTypeId());

        // Step 4: Lock wallets in ascending ID order — prevents deadlocks
        // This lock means: no other transaction can insert ledger entries for
        // these wallets until this transaction commits or rolls back.
        List<Long> sortedIds = Stream.of(userWallet.getId(), revenueWallet.getId())
                .sorted()
                .toList();
        walletRepo.lockForUpdate(sortedIds);

        // Step 5: Compute balance inside the lock — this is a serialized read.
        // Any concurrent spend that locked these wallets before us will have
        // already committed (updating the ledger) before we reach this point.
        long currentBalance = ledgerRepo.getBalance(req.getAccountId(), req.getAssetTypeId());

        // Step 6: Enforce non-negative balance invariant
        if (currentBalance < req.getAmount()) {
            // Rolling back will also undo the transaction row insert,
            // but because the idempotency key is already in the DB within
            // this transaction, the client will get a 422 on this and any
            // retry with the same key (until the transaction fully rolls back).
            // After rollback, the idempotency key is gone, so the client CAN
            // retry with a new key if they wish.
            throw new InsufficientFundsException(
                    req.getAccountId(), req.getAssetTypeId(), currentBalance, req.getAmount()
            );
        }

        // Step 7: Double-entry ledger (SUM = 0, balanced)
        ledgerRepo.insert(txn.getId(), userWallet.getId(),    -req.getAmount());  // User debited
        ledgerRepo.insert(txn.getId(), revenueWallet.getId(), +req.getAmount());  // Revenue credited

        return new TransactionResponse(txn, ledgerRepo.findByTransactionId(txn.getId()), false);
    }

    // =========================================================================
    // QUERY OPERATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(long accountId, long assetTypeId) {
        validateAccountExists(accountId);
        validateAssetTypeExists(assetTypeId);
        long balance = ledgerRepo.getBalance(accountId, assetTypeId);
        return new BalanceResponse(accountId, assetTypeId, balance);
    }

    @Transactional(readOnly = true)
    public LedgerResponse getLedger(long accountId, long assetTypeId, int page, int pageSize) {
        validateAccountExists(accountId);
        validateAssetTypeExists(assetTypeId);
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        List<LedgerEntryView> entries = ledgerRepo.getLedger(accountId, assetTypeId, page, pageSize);
        long total = ledgerRepo.countLedger(accountId, assetTypeId);
        return new LedgerResponse(entries, total, page, pageSize);
    }

    // =========================================================================
    // ACCOUNT & ASSET TYPE OPERATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepo.findAll();
    }

    @Transactional
    public Account createAccount(CreateAccountRequest req) {
        return accountRepo.save(req.getType(), req.getName());
    }

    @Transactional(readOnly = true)
    public List<com.dinoventures.wallet.model.AssetType> getAllAssetTypes() {
        return assetTypeRepo.findAll();
    }

    // =========================================================================
    // VALIDATION HELPERS
    // =========================================================================

    private void validateAccountExists(long accountId) {
        accountRepo.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private void validateAssetTypeExists(long assetTypeId) {
        assetTypeRepo.findById(assetTypeId)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetTypeId));
    }
}
