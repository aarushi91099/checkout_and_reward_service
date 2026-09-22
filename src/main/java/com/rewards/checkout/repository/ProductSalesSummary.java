package com.rewards.checkout.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductSalesSummary {
    UUID getProductId();

    String getProductName();

    Long getQuantitySold();

    BigDecimal getRevenue();
}
