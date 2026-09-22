package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CheckoutInventoryValidationIT extends AbstractIntegrationTest {

    @Test
    void checkoutFailsWhenRequestedQuantityExceedsAvailableInventory() {
        UUID productId = seedProduct("Scarce Widget", "5.00", 1);
        UUID cartId = createCart();
        addItem(cartId, productId, 2);

        ResponseEntity<String> response = checkout(cartId, "key-" + UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = readJson(response);
        assertThat(body.get("code").asText()).isEqualTo("INSUFFICIENT_INVENTORY");

        ResponseEntity<String> cartResponse = restTemplate.getForEntity(baseUrl() + "/carts/" + cartId, String.class);
        assertThat(readJson(cartResponse).get("status").asText()).isEqualTo("ACTIVE");
    }
}
