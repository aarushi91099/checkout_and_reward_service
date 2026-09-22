package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PriceChangeAtCheckoutIT extends AbstractIntegrationTest {

    @Test
    void checkoutUsesTheProductPriceAtLockTimeNotAtAddToCartTime() {
        UUID productId = seedProduct("Volatile Widget", "10.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 2);

        jdbcTemplate.update("update products set price = ? where id = ?", new BigDecimal("15.00"), productId);

        ResponseEntity<String> response = checkout(cartId, "key-" + UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode order = readJson(response);
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(order.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("15.00");
    }
}
