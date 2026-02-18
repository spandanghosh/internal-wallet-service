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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests that verify the SELECT FOR UPDATE locking strategy prevents
 * race conditions under simultaneous requests.
 *
 * Each test starts from a clean state (Alice = 500 Gold Coins).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql(
    scripts = {"/db/truncate.sql", "/db/seed.sql"},
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ConcurrencyTest {

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

    private static final long ALICE_ID = 3L;
    private static final long GOLD_ID  = 1L;

    /**
     * Ten threads simultaneously try to spend Alice's entire balance (500 coins).
     * Because only one DB transaction can hold the FOR UPDATE lock at a time,
     * exactly one spend succeeds; the other nine see a zero balance and get 422.
     *
     * This is the core correctness guarantee of the pessimistic locking strategy.
     */
    @Test
    void concurrentSpend_exactlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                // Prepare the request before the latch so all threads fire simultaneously
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Idempotency-Key", UUID.randomUUID().toString());
                Map<String, Object> body = Map.of(
                        "account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", 500L);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                ResponseEntity<Map> resp = restTemplate.exchange(
                        "/api/v1/transactions/spend", HttpMethod.POST, entity, Map.class);
                statusCodes.add(resp.getStatusCode().value());
            });
        }

        ready.await();   // wait until all threads are prepared
        start.countDown(); // release them all at once
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        long successes = statusCodes.stream().filter(c -> c == 201).count();
        long failures  = statusCodes.stream().filter(c -> c == 422).count();

        assertThat(successes).as("exactly one spend should succeed").isEqualTo(1);
        assertThat(failures).as("all other spends should fail with 422").isEqualTo(9);

        // Final balance must be 0 — one successful spend of 500
        assertThat(getBalance(ALICE_ID, GOLD_ID)).isEqualTo(0);
    }

    /**
     * Five threads simultaneously top up Alice's wallet with different amounts.
     * All topups must succeed, and the final balance must equal the sum of all
     * credited amounts. This verifies that no updates are lost under concurrency.
     */
    @Test
    void concurrentTopup_allSucceed() throws InterruptedException {
        int threadCount = 5;
        long amountEach = 100L;

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Idempotency-Key", UUID.randomUUID().toString());
                Map<String, Object> body = Map.of(
                        "account_id", ALICE_ID, "asset_type_id", GOLD_ID, "amount", amountEach);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                ResponseEntity<Map> resp = restTemplate.exchange(
                        "/api/v1/transactions/topup", HttpMethod.POST, entity, Map.class);
                statusCodes.add(resp.getStatusCode().value());
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(statusCodes).as("all topups should succeed").allMatch(c -> c == 201);

        long expectedBalance = 500 + threadCount * amountEach; // 500 + 5×100 = 1000
        assertThat(getBalance(ALICE_ID, GOLD_ID))
                .as("final balance must equal sum of all topup amounts")
                .isEqualTo(expectedBalance);
    }

    private long getBalance(long accountId, long assetTypeId) {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/accounts/{id}/balance?asset_type_id={atid}",
                Map.class, accountId, assetTypeId);
        return ((Number) resp.getBody().get("balance")).longValue();
    }
}
