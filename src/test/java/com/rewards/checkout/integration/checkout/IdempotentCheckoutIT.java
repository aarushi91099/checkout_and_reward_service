package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IdempotentCheckoutIT extends AbstractIntegrationTest {

    @Test
    void sameIdempotencyKeySentTwiceSequentiallyCreatesOnlyOneOrder() {
        UUID productId = seedProduct("Widget", "12.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 1);
        String idempotencyKey = "key-" + UUID.randomUUID();

        ResponseEntity<String> first = checkout(cartId, idempotencyKey, null);
        ResponseEntity<String> second = checkout(cartId, idempotencyKey, null);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode firstBody = readJson(first);
        JsonNode secondBody = readJson(second);
        assertThat(secondBody.get("id").asText()).isEqualTo(firstBody.get("id").asText());
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeySentConcurrentlyStillCreatesOnlyOneOrder() throws Exception {
        UUID productId = seedProduct("Widget", "12.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 1);
        String idempotencyKey = "key-" + UUID.randomUUID();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> futures = List.of(
                    executor.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return checkout(cartId, idempotencyKey, null);
                    }),
                    executor.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return checkout(cartId, idempotencyKey, null);
                    }));

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<ResponseEntity<String>> responses = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            for (ResponseEntity<String> response : responses) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            }
            String firstOrderId = readJson(responses.get(0)).get("id").asText();
            String secondOrderId = readJson(responses.get(1)).get("id").asText();
            assertThat(secondOrderId).isEqualTo(firstOrderId);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
    }
}
