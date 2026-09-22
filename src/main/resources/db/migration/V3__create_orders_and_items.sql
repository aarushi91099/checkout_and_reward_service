CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    cart_id         UUID NOT NULL REFERENCES carts (id),
    subtotal        NUMERIC(19, 2) NOT NULL,
    discount        NUMERIC(19, 2) NOT NULL,
    total           NUMERIC(19, 2) NOT NULL,
    coupon_code     VARCHAR(64),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_orders_cart_id UNIQUE (cart_id)
);

CREATE TABLE order_items (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders (id),
    product_id      UUID NOT NULL,
    product_name    VARCHAR(255) NOT NULL,
    unit_price      NUMERIC(19, 2) NOT NULL,
    quantity        INTEGER NOT NULL
);
