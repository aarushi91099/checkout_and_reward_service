package com.rewards.checkout.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity) {
}
