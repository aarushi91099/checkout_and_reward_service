package com.rewards.checkout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.domain.entity.Product;
import com.rewards.checkout.repository.CouponRepository;
import com.rewards.checkout.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for every integration test. Runs against a real Postgres via Testcontainers - required
 * because H2 does not implement real SELECT ... FOR UPDATE blocking semantics, and every
 * concurrency invariant in this service depends on real row-level locking behaviour.
 *
 * Uses the Testcontainers "singleton container" pattern (a plain static field started once in a
 * static initializer, not @Container/@Testcontainers) so every IT class shares one Postgres
 * instance and, since the resulting datasource properties are identical across classes, one
 * cached Spring ApplicationContext - avoiding a container and connection pool per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("checkout_reward_test")
            .withUsername("checkout")
            .withPassword("checkout")
            .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CouponRepository couponRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE checkout_requests, order_items, orders, cart_items, carts, coupons, "
                + "products RESTART IDENTITY CASCADE");
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected UUID seedProduct(String name, String price, int inventory) {
        Product product = productRepository.save(new Product(UUID.randomUUID(), name, new BigDecimal(price), inventory));
        return product.getId();
    }

    protected void seedCoupon(String code, String discountPercent, long milestoneNumber) {
        couponRepository.save(new Coupon(code, new BigDecimal(discountPercent), milestoneNumber));
    }

    protected UUID createCart() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl() + "/carts", null, String.class);
        return UUID.fromString(readJson(response).get("id").asText());
    }

    protected void addItem(UUID cartId, UUID productId, int quantity) {
        String body = "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(baseUrl() + "/carts/" + cartId + "/items", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> checkout(UUID cartId, String idempotencyKey, String couponCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        String body = couponCode != null ? "{\"couponCode\":\"" + couponCode + "\"}" : "{}";
        return restTemplate.exchange(baseUrl() + "/carts/" + cartId + "/checkout", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
