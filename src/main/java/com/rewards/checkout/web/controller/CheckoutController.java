package com.rewards.checkout.web.controller;

import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.service.CheckoutService;
import com.rewards.checkout.web.dto.CheckoutRequestBody;
import com.rewards.checkout.web.dto.OrderResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts/{cartId}/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@PathVariable UUID cartId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @RequestBody(required = false) CheckoutRequestBody body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required");
        }
        String couponCode = body != null ? body.couponCode() : null;
        OrderResponse response = checkoutService.checkout(cartId, idempotencyKey, couponCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
