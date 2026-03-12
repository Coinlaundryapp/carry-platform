-- V4__create_price_tables.sql

CREATE TABLE price_policies
(
    id                 BIGSERIAL PRIMARY KEY,
    order_unit_type    VARCHAR(50)                            NOT NULL,
    order_request_type VARCHAR(50)                            NOT NULL,
    laundry_item_type  VARCHAR(50)                            NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (order_unit_type, order_request_type, laundry_item_type)
);

CREATE TABLE price_option_prices
(
    id              BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT                                 NOT NULL REFERENCES price_policies (id) ON DELETE CASCADE,
    option_type     VARCHAR(50)                            NOT NULL,
    sub_option_type VARCHAR(50)                            NOT NULL,
    price           INT                                    NOT NULL CHECK (price >= 0),
    selectable      BOOLEAN                  DEFAULT true  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (policy_id, option_type, sub_option_type)
);

CREATE INDEX idx_price_option_prices_policy_id ON price_option_prices (policy_id);
