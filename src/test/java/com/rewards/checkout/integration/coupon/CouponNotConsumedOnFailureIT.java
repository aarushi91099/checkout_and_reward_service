package com.rewards.checkout.integration.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewards.checkout.domain.enums.CouponStatus;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CouponNotConsumedOnFailureIT extends AbstractIntegrationTest {

    @Test
    void couponRemainsAvailableWhenCheckoutFailsOnInsufficientInventory() {
        UUID productId = seedProduct("Scarce Widget", "8.00", 1);
        seedCoupon("KEEP10", "10", 9002L);
        UUID cartId = createCart();
        addItem(cartId, productId, 2);

        ResponseEntity<String> response = checkout(cartId, "key-" + UUID.randomUUID(), "KEEP10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(response).get("code").asText()).isEqualTo("INSUFFICIENT_INVENTORY");
        assertThat(couponRepository.findById("KEEP10").orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE);
    }
}
