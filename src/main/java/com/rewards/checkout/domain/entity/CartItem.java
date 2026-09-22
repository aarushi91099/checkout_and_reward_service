package com.rewards.checkout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    protected CartItem() {
    }

    public CartItem(UUID id, UUID cartId, UUID productId, int quantity) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCartId() {
        return cartId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
