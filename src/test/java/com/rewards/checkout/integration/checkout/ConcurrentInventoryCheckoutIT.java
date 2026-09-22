package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

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

class ConcurrentInventoryCheckoutIT extends AbstractIntegrationTest {

    @Test
    void exactlyOneOfTwoConcurrentCheckoutsForTheLastUnitSucceeds() throws Exception {
        UUID productId = seedProduct("Last Unit", "15.00", 1);
        UUID cartA = createCart();
        UUID cartB = createCart();
        addItem(cartA, productId, 1);
        addItem(cartB, productId, 1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> futures = List.of(
                    executor.submit(() -> race(cartA, ready, start)),
                    executor.submit(() -> race(cartB, ready, start)));

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<HttpStatus> statuses = futures.stream()
                    .map(f -> {
                        try {
                            return (HttpStatus) f.get(30, TimeUnit.SECONDS).getStatusCode();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            executor.shutdownNow();
        }

        assertThat(productRepository.findById(productId).orElseThrow().getInventory()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
    }

    private ResponseEntity<String> race(UUID cartId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return checkout(cartId, "key-" + cartId, null);
    }
}
