-- Operation module tables

CREATE TABLE operation_terms (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(255)    NOT NULL,
    content     TEXT            NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    required    BOOLEAN         NOT NULL DEFAULT true,
    version     INTEGER         NOT NULL DEFAULT 1,
    active      BOOLEAN         NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_operation_terms_active ON operation_terms (active);
CREATE INDEX idx_operation_terms_required_active ON operation_terms (required, active);

CREATE TABLE operation_events (
    id              BIGSERIAL       PRIMARY KEY,
    event_type      VARCHAR(50)     NOT NULL,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    BIGINT          NOT NULL,
    summary         VARCHAR(500)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_operation_events_created_at ON operation_events (created_at DESC);
CREATE INDEX idx_operation_events_type_created ON operation_events (event_type, created_at);
