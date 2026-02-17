package com.dinoventures.wallet.controller;

import com.dinoventures.wallet.model.dto.*;
import com.dinoventures.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final WalletService walletService;

    /**
     * POST /api/v1/transactions/topup
     *
     * Credits a user's wallet as if they purchased credits with real money.
     * The payment system is assumed to have already succeeded.
     *
     * Required header: Idempotency-Key (client-generated UUID)
     * Returns 201 if new transaction, 200 if idempotent replay.
     */
    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topup(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopupRequest req) {

        TransactionResponse response = walletService.topup(req, idempotencyKey);
        return ResponseEntity
                .status(response.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /api/v1/transactions/bonus
     *
     * Issues free credits (e.g., referral bonus, welcome reward) from the
     * system Treasury to a user's wallet.
     *
     * Required header: Idempotency-Key (client-generated UUID)
     * Returns 201 if new transaction, 200 if idempotent replay.
     */
    @PostMapping("/bonus")
    public ResponseEntity<TransactionResponse> bonus(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BonusRequest req) {

        TransactionResponse response = walletService.bonus(req, idempotencyKey);
        return ResponseEntity
                .status(response.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }

    /**
     * POST /api/v1/transactions/spend
     *
     * Debits a user's wallet when they purchase an in-app service or item.
     * Returns 422 if the user has insufficient funds.
     *
     * Required header: Idempotency-Key (client-generated UUID)
     * Returns 201 if new transaction, 200 if idempotent replay.
     */
    @PostMapping("/spend")
    public ResponseEntity<TransactionResponse> spend(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SpendRequest req) {

        TransactionResponse response = walletService.spend(req, idempotencyKey);
        return ResponseEntity
                .status(response.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }
}
