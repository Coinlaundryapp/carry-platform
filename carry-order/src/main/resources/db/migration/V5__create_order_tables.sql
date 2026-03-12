CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    laundromat_id   BIGINT NOT NULL,
    laundry_item_type VARCHAR(30) NOT NULL,
    road_address    VARCHAR(255) NOT NULL,
    detail_address  VARCHAR(255) NOT NULL,
    zip_code        VARCHAR(10),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    recipient_name  VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    entrance_info   VARCHAR(255),
    desired_pickup_at  TIMESTAMPTZ NOT NULL,
    desired_delivery_at TIMESTAMPTZ NOT NULL,
    carrier_id      BIGINT,
    invoice_id      BIGINT,
    total_amount    BIGINT,
    actual_weight   NUMERIC(10,2),
    cancel_reason   VARCHAR(255),
    cancelled_by    VARCHAR(20),
    cancelled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_selected_options (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    option_type     VARCHAR(30) NOT NULL,
    sub_option_type VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_options_order_id ON order_selected_options(order_id);
