package com.rewards.checkout.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(@NotNull Integer quantity) {
}
