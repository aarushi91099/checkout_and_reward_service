package com.rewards.checkout.web.dto;

import com.rewards.checkout.domain.enums.CartStatus;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID id, CartStatus status, List<CartItemResponse> items) {
}
