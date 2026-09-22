-- Not a Flyway migration (lives outside db/migration) and not applied automatically.
-- PLAN.md's API surface has no product-management endpoint, so products can only be
-- provisioned by inserting directly. Run manually against a local dev database for the
-- README's example requests, e.g.:
--   docker-compose up -d
--   psql postgresql://checkout:checkout@localhost:5432/checkout_reward \
--     -f src/main/resources/db/seed/seed-demo-products.sql

INSERT INTO products (id, name, price, inventory) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Wireless Mouse', 24.99, 50),
    ('22222222-2222-2222-2222-222222222222', 'Mechanical Keyboard', 89.99, 20),
    ('33333333-3333-3333-3333-333333333333', 'USB-C Hub', 34.50, 5)
ON CONFLICT (id) DO NOTHING;
