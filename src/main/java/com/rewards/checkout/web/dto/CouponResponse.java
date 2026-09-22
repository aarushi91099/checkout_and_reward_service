package com.rewards.checkout.web.dto;

import com.rewards.checkout.domain.enums.CouponStatus;
import java.math.BigDecimal;

public record CouponResponse(String code, BigDecimal discountPercent, CouponStatus status, long milestoneNumber) {
}
