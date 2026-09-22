CREATE TABLE coupons (
    code                    VARCHAR(64) PRIMARY KEY,
    discount_percent        NUMERIC(5, 2) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    milestone_number        BIGINT NOT NULL,
    redeemed_by_order_id    UUID REFERENCES orders (id),
    created_at              TIMESTAMP NOT NULL,
    redeemed_at             TIMESTAMP,
    CONSTRAINT uq_coupons_milestone_number UNIQUE (milestone_number)
);
