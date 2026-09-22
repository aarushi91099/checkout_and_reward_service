package com.rewards.checkout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable once persisted: no setters, all fields fixed at construction.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal discount;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Order() {
    }

    public Order(UUID id, UUID cartId, BigDecimal subtotal, BigDecimal discount, BigDecimal total, String couponCode) {
        this.id = id;
        this.cartId = cartId;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.couponCode = couponCode;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCartId() {
        return cartId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
