package com.rewards.checkout.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return normalize(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal discountFor(BigDecimal subtotal, BigDecimal discountPercent) {
        return normalize(subtotal.multiply(discountPercent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    public static BigDecimal totalAfterDiscount(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal total = subtotal.subtract(discount);
        return total.compareTo(BigDecimal.ZERO) < 0 ? normalize(BigDecimal.ZERO) : normalize(total);
    }
}
