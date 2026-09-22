CREATE TABLE carts (
    id          UUID PRIMARY KEY,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE TABLE cart_items (
    id          UUID PRIMARY KEY,
    cart_id     UUID NOT NULL REFERENCES carts (id),
    product_id  UUID NOT NULL REFERENCES products (id),
    quantity    INTEGER NOT NULL,
    CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);
