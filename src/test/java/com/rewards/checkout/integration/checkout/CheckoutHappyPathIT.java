package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CheckoutHappyPathIT extends AbstractIntegrationTest {

    @Test
    void createCartAddItemCheckoutAndRetrieveOrder() {
        UUID productId = seedProduct("Widget", "10.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 2);

        ResponseEntity<String> checkoutResponse = checkout(cartId, "key-" + UUID.randomUUID(), null);
        assertThat(checkoutResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode order = readJson(checkoutResponse);
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(order.get("discount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("20.00");
        String orderId = order.get("id").asText();

        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl() + "/orders/" + orderId, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode fetched = readJson(getResponse);
        assertThat(fetched.get("id").asText()).isEqualTo(orderId);
        assertThat(fetched.get("items")).hasSize(1);

        ResponseEntity<String> productAfter = restTemplate.getForEntity(baseUrl() + "/carts/" + cartId, String.class);
        assertThat(readJson(productAfter).get("status").asText()).isEqualTo("CHECKED_OUT");
    }
}
