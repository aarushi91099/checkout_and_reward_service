package com.rewards.checkout.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID cartId,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String couponCode,
        Instant createdAt,
        List<OrderItemResponse> items) {
}
