CREATE TABLE delivery_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    dispatch_id     BIGINT NOT NULL,
    carrier_id      BIGINT NOT NULL,
    laundromat_id   BIGINT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    actual_weight   NUMERIC(10,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_deliveries_order_id ON delivery_deliveries(order_id);
CREATE INDEX idx_delivery_deliveries_carrier_id ON delivery_deliveries(carrier_id);
CREATE INDEX idx_delivery_deliveries_status ON delivery_deliveries(status);

CREATE TABLE delivery_steps (
    id              BIGSERIAL PRIMARY KEY,
    delivery_id     BIGINT NOT NULL REFERENCES delivery_deliveries(id),
    step_type       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    note            VARCHAR(500),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_steps_delivery_id ON delivery_steps(delivery_id);

CREATE TABLE delivery_step_media (
    id              BIGSERIAL PRIMARY KEY,
    delivery_step_id BIGINT NOT NULL REFERENCES delivery_steps(id),
    media_id        BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_step_media_step_id ON delivery_step_media(delivery_step_id);
