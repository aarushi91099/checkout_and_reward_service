package com.rewards.checkout.domain.entity;

import com.rewards.checkout.domain.enums.CouponStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    private String code;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "milestone_number", nullable = false)
    private long milestoneNumber;

    @Column(name = "redeemed_by_order_id")
    private UUID redeemedByOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    protected Coupon() {
    }

    public Coupon(String code, BigDecimal discountPercent, long milestoneNumber) {
        this.code = code;
        this.discountPercent = discountPercent;
        this.status = CouponStatus.AVAILABLE;
        this.milestoneNumber = milestoneNumber;
        this.createdAt = Instant.now();
    }

    public void redeem(UUID orderId) {
        if (status != CouponStatus.AVAILABLE) {
            throw new IllegalStateException("Coupon is not available for redemption");
        }
        this.status = CouponStatus.REDEEMED;
        this.redeemedByOrderId = orderId;
        this.redeemedAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public long getMilestoneNumber() {
        return milestoneNumber;
    }

    public UUID getRedeemedByOrderId() {
        return redeemedByOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
