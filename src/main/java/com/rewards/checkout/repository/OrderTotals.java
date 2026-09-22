package com.rewards.checkout.repository;

import java.math.BigDecimal;

public interface OrderTotals {
    BigDecimal getGrossRevenue();

    BigDecimal getDiscounts();

    BigDecimal getNetRevenue();
}
