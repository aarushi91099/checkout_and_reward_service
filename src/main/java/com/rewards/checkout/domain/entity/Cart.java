package com.rewards.checkout.domain.entity;

import com.rewards.checkout.domain.enums.CartStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Cart() {
    }

    public Cart(UUID id) {
        this.id = id;
        this.status = CartStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void markCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
    }

    public UUID getId() {
        return id;
    }

    public CartStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
