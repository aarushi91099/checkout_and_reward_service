package com.rewards.checkout.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReportResponse(
        long totalOrders,
        BigDecimal grossRevenue,
        BigDecimal discounts,
        BigDecimal netRevenue,
        List<ProductSummaryResponse> products,
        CouponSummaryResponse couponSummary) {
}
