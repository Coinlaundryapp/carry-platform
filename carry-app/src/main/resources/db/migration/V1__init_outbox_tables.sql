-- Outbox events table (used by Debezium CDC)
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    trace_id        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Processed events table (for consumer idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    id              VARCHAR(100) PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for outbox housekeeping
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);
