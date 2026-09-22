CREATE TABLE checkout_requests (
    idempotency_key     VARCHAR(255) PRIMARY KEY,
    cart_id             UUID NOT NULL REFERENCES carts (id),
    order_id            UUID NOT NULL REFERENCES orders (id),
    created_at          TIMESTAMP NOT NULL
);
