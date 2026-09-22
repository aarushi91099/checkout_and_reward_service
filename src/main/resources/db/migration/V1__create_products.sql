CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    price       NUMERIC(19, 2) NOT NULL,
    inventory   INTEGER NOT NULL,
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_products_inventory_non_negative CHECK (inventory >= 0)
);
