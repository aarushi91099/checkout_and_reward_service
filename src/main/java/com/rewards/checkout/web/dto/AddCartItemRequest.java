package com.rewards.checkout.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemRequest(@NotNull UUID productId, @NotNull Integer quantity) {
}
