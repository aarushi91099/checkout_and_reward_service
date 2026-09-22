package com.rewards.checkout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency record. A row is inserted only as the final step of a
 * successful checkout, in the same transaction as order creation. Failed or
 * interrupted attempts roll back entirely, so existence of a row always
 * means "completed successfully" - no separate status column is needed.
 */
@Entity
@Table(name = "checkout_requests")
public class CheckoutRequest {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutRequest() {
    }

    public CheckoutRequest(String idempotencyKey, UUID cartId, UUID orderId) {
        this.idempotencyKey = idempotencyKey;
        this.cartId = cartId;
        this.orderId = orderId;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCartId() {
        return cartId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
