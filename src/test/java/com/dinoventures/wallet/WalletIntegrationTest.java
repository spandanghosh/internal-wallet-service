package com.dinoventures.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests: HTTP → Controller → Service → PostgreSQL (Testcontainers).
 *
 * Each test method starts from a clean, deterministic database state:
 *   - Alice  (account_id=3): 500 Gold Coins
 *   - Bob    (account_id=4): 200 Gold Coins
 *   - Asset type Gold Coins: asset_type_id=1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql(
    scripts = {"/db/truncate.sql", "/db/seed.sql"},
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class WalletIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Set spring.datasource.* directly — bypasses the ${SPRING_DATASOURCE_URL}
        // placeholder, which EnvironmentPostProcessors cannot resolve in tests.
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // Seeded constants — must match db/seed.sql
    private static final long ALICE_ID  = 3L;
    private static final long BOB_ID    = 4L;
    private static final long GOLD_ID   = 1L;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private ResponseEntity<Map> post(String path, String idempotencyKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private long getBalance(long accountId, long assetTypeId) {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/accounts/{id}/balance?asset_type_id={atid}",
                Map.class, accountId, assetTypeId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("balance")).longValue();
    }

    // =========================================================================
    // Topup tests
    // =========================================================================

    @Test
    void topup_creditsUserWallet() {
        ResponseEntity<Map> resp = post(
                "/api/v1/transactions/topup",
                UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 100L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("idempotent")).isEqualTo(false);

        Map<?, ?> tx = (Map<?, ?>) resp.getBody().get("transaction");
        assertThat(tx.get("type")).isEqualTo("topup");
        assertThat(tx.get("status")).isEqualTo("completed");

        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(600); // 500 + 100
    }

    // =========================================================================
    // Bonus tests
    // =========================================================================

    @Test
    void bonus_creditsUserWallet() {
        ResponseEntity<Map> resp = post(
                "/api/v1/transactions/bonus",
                UUID.randomUUID().toString(),
                Map.of("account_id", BOB_ID, "asset_type_id", GOLD_ID, "amount", 50L,
                       "description", "Referral bonus"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> tx = (Map<?, ?>) resp.getBody().get("transaction");
        assertThat(tx.get("type")).isEqualTo("bonus");

        assertThat(getBalance(BOB_ID, GOLD_ID)).isEqualTo(250); // 200 + 50
    }

    // =========================================================================
    // Spend tests
    // =========================================================================

    @Test
    void spend_debitsUserWallet() {
        ResponseEntity<Map> resp = post(
                "/api/v1/transactions/spend",
                UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 200L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(300); // 500 - 200
    }

    @Test
    void spend_insufficientFunds_returns422() {
        ResponseEntity<Map> resp = post(
                "/api/v1/transactions/spend",
                UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 9999L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("error").toString()).contains("Insufficient funds");

        // Balance must be unchanged — the transaction was rolled back
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(500);
    }

    @Test
    void spend_missingIdempotencyKey_returns400() {
        ResponseEntity<Map> resp = post(
                "/api/v1/transactions/spend",
                null, // no Idempotency-Key header
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 100L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error").toString()).contains("Idempotency-Key");
    }

    // =========================================================================
    // Idempotency tests
    // =========================================================================

    @Test
    void topup_idempotentReplay_returns200AndBalanceUnchanged() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 100L);

        // First call — new request
        ResponseEntity<Map> first = post("/api/v1/transactions/topup", key, body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("idempotent")).isEqualTo(false);

        // Second call — same key, must be a replay
        ResponseEntity<Map> second = post("/api/v1/transactions/topup", key, body);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("idempotent")).isEqualTo(true);

        // Only one topup should have applied
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(600); // 500 + 100 once
    }

    @Test
    void spend_idempotentReplay_returns200AndBalanceUnchanged() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 100L);

        ResponseEntity<Map> first = post("/api/v1/transactions/spend", key, body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = post("/api/v1/transactions/spend", key, body);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("idempotent")).isEqualTo(true);

        // Only one spend should have applied
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(400); // 500 - 100 once
    }

    // =========================================================================
    // Balance and ledger tests
    // =========================================================================

    @Test
    void balance_reflectsLedgerEntries() {
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(500);
        assertThat(getBalance(BOB_ID, GOLD_ID)).isEqualTo(200);

        post("/api/v1/transactions/topup", UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 300L));

        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(800);
        assertThat(getBalance(BOB_ID, GOLD_ID)).isEqualTo(200); // unaffected
    }

    @Test
    @SuppressWarnings("unchecked")
    void ledger_returnsEntriesNewestFirst() {
        // Two topups in order: 100 then 200
        post("/api/v1/transactions/topup", UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 100L));
        post("/api/v1/transactions/topup", UUID.randomUUID().toString(),
                Map.of("account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 200L));

        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/accounts/{id}/ledger?asset_type_id={atid}&page=1&page_size=5",
                Map.class, ALICE_ID, GOLD_ID);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) resp.getBody().get("entries");

        // At least 3 entries: initial seed (+500), topup +100, topup +200
        assertThat(entries.size()).isGreaterThanOrEqualTo(3);

        // Newest first: the +200 entry must appear before the +100 entry
        long first  = ((Number) entries.get(0).get("amount")).longValue();
        long second = ((Number) entries.get(1).get("amount")).longValue();
        assertThat(first).isEqualTo(200);
        assertThat(second).isEqualTo(100);

        long total = ((Number) resp.getBody().get("total")).longValue();
        assertThat(total).isGreaterThanOrEqualTo(3);
    }
}
