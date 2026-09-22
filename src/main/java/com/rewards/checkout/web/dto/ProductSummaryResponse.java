package com.rewards.checkout.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(UUID productId, String productName, long quantitySold, BigDecimal revenue) {
}
