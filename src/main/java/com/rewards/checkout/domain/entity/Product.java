package com.rewards.checkout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int inventory;

    protected Product() {
    }

    public Product(UUID id, String name, BigDecimal price, int inventory) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.inventory = inventory;
    }

    public void deductInventory(int quantity) {
        if (quantity > inventory) {
            throw new IllegalStateException("Cannot deduct more than available inventory");
        }
        inventory -= quantity;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getInventory() {
        return inventory;
    }
}
