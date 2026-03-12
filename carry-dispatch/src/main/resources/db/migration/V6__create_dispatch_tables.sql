CREATE TABLE dispatch_dispatches (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL UNIQUE,
    laundromat_id       BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    carrier_id          BIGINT,
    area_code           VARCHAR(20) NOT NULL,
    desired_pickup_at   TIMESTAMPTZ NOT NULL,
    assigned_by         VARCHAR(20),
    assigned_at         TIMESTAMPTZ,
    accepted_at         TIMESTAMPTZ,
    cancel_reason       VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispatch_dispatches_order_id ON dispatch_dispatches(order_id);
CREATE INDEX idx_dispatch_dispatches_status ON dispatch_dispatches(status);
CREATE INDEX idx_dispatch_dispatches_area_code ON dispatch_dispatches(area_code);
CREATE INDEX idx_dispatch_dispatches_carrier_id ON dispatch_dispatches(carrier_id);
CREATE INDEX idx_dispatch_dispatches_status_area ON dispatch_dispatches(status, area_code);

CREATE TABLE dispatch_carrier_areas (
    id              BIGSERIAL PRIMARY KEY,
    carrier_id      BIGINT NOT NULL,
    area_code       VARCHAR(20) NOT NULL,
    area_name       VARCHAR(50) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (carrier_id, area_code)
);

CREATE INDEX idx_dispatch_carrier_areas_carrier_id ON dispatch_carrier_areas(carrier_id);
CREATE INDEX idx_dispatch_carrier_areas_area_code ON dispatch_carrier_areas(area_code);

CREATE TABLE dispatch_penalty_records (
    id              BIGSERIAL PRIMARY KEY,
    carrier_id      BIGINT NOT NULL,
    dispatch_id     BIGINT NOT NULL REFERENCES dispatch_dispatches(id),
    reason          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispatch_penalty_records_carrier_id ON dispatch_penalty_records(carrier_id);
CREATE INDEX idx_dispatch_penalty_records_dispatch_id ON dispatch_penalty_records(dispatch_id);
