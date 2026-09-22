package com.rewards.checkout.integration.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class CheckoutValidationIT extends AbstractIntegrationTest {

    @Test
    void checkoutWithoutIdempotencyKeyHeaderIsRejected() {
        UUID productId = seedProduct("Widget", "10.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/carts/" + cartId + "/checkout",
                HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void checkoutOnAnAlreadyCheckedOutCartWithANewKeyIsRejected() {
        UUID productId = seedProduct("Widget", "10.00", 5);
        UUID cartId = createCart();
        addItem(cartId, productId, 1);
        checkout(cartId, "key-" + UUID.randomUUID(), null);

        ResponseEntity<String> second = checkout(cartId, "key-" + UUID.randomUUID(), null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readJson(second).get("code").asText()).isEqualTo("CART_ALREADY_CHECKED_OUT");
    }

    @Test
    void addingAnInvalidQuantityIsRejected() {
        UUID productId = seedProduct("Widget", "10.00", 5);
        UUID cartId = createCart();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"productId\":\"" + productId + "\",\"quantity\":0}";
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/carts/" + cartId + "/items",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readJson(response).get("code").asText()).isEqualTo("INVALID_QUANTITY");
    }

    @Test
    void addingAnUnknownProductIsRejected() {
        UUID cartId = createCart();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}";
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/carts/" + cartId + "/items",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readJson(response).get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }
}
