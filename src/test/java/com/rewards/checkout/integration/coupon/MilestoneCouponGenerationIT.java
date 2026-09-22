package com.rewards.checkout.integration.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Default reward.every-nth-order = 5 (see application.yml).
 */
class MilestoneCouponGenerationIT extends AbstractIntegrationTest {

    @Test
    void exactlyOneCouponIsGeneratedPerMilestoneAsOrderCountGrows() {
        UUID productId = seedProduct("Widget", "5.00", 1000);

        placeOrders(productId, 5);
        assertMilestoneCount(1);

        placeOrders(productId, 5);
        assertMilestoneCount(2);

        placeOrders(productId, 5);
        assertMilestoneCount(3);

        List<Coupon> coupons = couponRepository.findAll();
        assertThat(coupons).extracting(Coupon::getMilestoneNumber).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void concurrentOrdersCrossingTheSameMilestoneBoundaryGenerateOnlyOneCoupon() throws Exception {
        UUID productId = seedProduct("Widget", "5.00", 1000);
        placeOrders(productId, 4);

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
            for (Future<ResponseEntity<String>> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertMilestoneCount(1);
    }

    private ResponseEntity<String> race(UUID cartId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return checkout(cartId, "key-" + cartId, null);
    }

    private void placeOrders(UUID productId, int count) {
        for (int i = 0; i < count; i++) {
            UUID cartId = createCart();
            addItem(cartId, productId, 1);
            checkout(cartId, "key-" + UUID.randomUUID(), null);
        }
    }

    private void assertMilestoneCount(long milestoneNumber) {
        long count = couponRepository.findAll().stream()
                .filter(c -> c.getMilestoneNumber() == milestoneNumber)
                .count();
        assertThat(count).isEqualTo(1);
    }
}
