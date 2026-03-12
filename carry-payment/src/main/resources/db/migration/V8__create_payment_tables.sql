CREATE TABLE payment_invoices (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    weight          NUMERIC(10,2) NOT NULL,
    total_amount    BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_payment_invoices_order_id ON payment_invoices(order_id);
CREATE INDEX idx_payment_invoices_customer_id ON payment_invoices(customer_id);
CREATE INDEX idx_payment_invoices_status ON payment_invoices(status);

CREATE TABLE payment_invoice_line_items (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES payment_invoices(id),
    charge_type     VARCHAR(30) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_line_items_invoice_id ON payment_invoice_line_items(invoice_id);

CREATE TABLE payment_payments (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_id          BIGINT NOT NULL REFERENCES payment_invoices(id),
    order_id            BIGINT NOT NULL,
    customer_id         BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    pg_provider         VARCHAR(30) NOT NULL,
    pg_transaction_id   VARCHAR(255),
    amount              BIGINT NOT NULL,
    paid_at             TIMESTAMPTZ,
    fail_reason         VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_payments_order_id ON payment_payments(order_id);
CREATE INDEX idx_payment_payments_customer_id ON payment_payments(customer_id);
CREATE INDEX idx_payment_payments_status ON payment_payments(status);
