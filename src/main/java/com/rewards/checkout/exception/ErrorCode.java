package com.rewards.checkout.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CART_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_INVENTORY(HttpStatus.CONFLICT),
    CART_ALREADY_CHECKED_OUT(HttpStatus.CONFLICT),
    CART_EMPTY(HttpStatus.BAD_REQUEST),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_ALREADY_REDEEMED(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
