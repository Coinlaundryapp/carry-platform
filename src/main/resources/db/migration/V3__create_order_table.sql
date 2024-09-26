ALTER TABLE service_availability_notifications
    ALTER COLUMN notification_type TYPE VARCHAR(255);

CREATE TABLE orders
(
    id                        BIGSERIAL PRIMARY KEY,
    status                    VARCHAR(100)                           NOT NULL,
    customer_id               BIGINT                                 NOT NULL,
    order_unit_type           VARCHAR(100)                           NOT NULL,
    order_request_type        VARCHAR(100)                           NOT NULL,
    laundry_item_type         VARCHAR(100)                           NOT NULL,
    laundromat_id             BIGINT                                 NOT NULL,
    laundromat_name           VARCHAR(100)                           NOT NULL,
    desired_pickup_datetime   VARCHAR(100)                           NOT NULL,
    desired_delivery_datetime VARCHAR(100)                           NOT NULL,
    estimated_amount          BIGINT                                 NOT NULL,
    ordered_at                TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- CREATE TABLE order_specs
-- (
--     id         BIGSERIAL PRIMARY KEY,
--     order_id   BIGINT REFERENCES orders (id) ON DELETE CASCADE,
--     spec_type  VARCHAR(100)                           NOT NULL,
--     value      INT                                    NOT NULL,
--     created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );
--
-- CREATE TABLE order_options
-- (
--     id              BIGSERIAL PRIMARY KEY,
--     order_id        BIGINT REFERENCES orders (id) ON DELETE CASCADE,
--     option_type     VARCHAR(100)                           NOT NULL,
--     sub_option_type VARCHAR(100)                           NOT NULL,
--     price           INT                                    NOT NULL,
--     created_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );
--
-- CREATE TABLE order_shipping_addresses
-- (
--     id              BIGSERIAL PRIMARY KEY,
--     order_id        BIGINT REFERENCES orders (id) ON DELETE CASCADE,
--     address_label   VARCHAR(255)                           NOT NULL,
--     recipient_name  VARCHAR(255)                           NOT NULL,
--     recipient_phone VARCHAR(255)                           NOT NULL,
--     base_address    VARCHAR(255)                           NOT NULL,
--     detail_address  VARCHAR(255),
--     delivery_notes  VARCHAR(255),
--     entrance_type   VARCHAR(255)                           NOT NULL,
--     entrance_detail VARCHAR(255),
--     latitude        DOUBLE PRECISION                       NOT NULL,
--     longitude       DOUBLE PRECISION                       NOT NULL,
--     created_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );
--

--
-- CREATE TABLE invoices
-- (
--     id              BIGSERIAL PRIMARY KEY,
--     order_id        BIGINT REFERENCES orders (id) ON DELETE CASCADE,
--     total_amount    INT                                    NOT NULL,
--     discount_amount INT                                    NOT NULL,
--     net_amount      INT                                    NOT NULL,
--     created_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at      TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );
--
-- CREATE TABLE invoice_charges
-- (
--     id          BIGSERIAL PRIMARY KEY,
--     invoice_id  BIGINT REFERENCES invoices (id) ON DELETE CASCADE,
--     charge_type VARCHAR(100)                           NOT NULL,
--     amount      INT                                    NOT NULL,
--     created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );
--
-- CREATE TABLE payments
-- (
--     id          BIGSERIAL PRIMARY KEY,
--     order_id    BIGINT REFERENCES orders (id) ON DELETE CASCADE,
--     payment_key VARCHAR(255)                           NOT NULL,
--     amount      INT                                    NOT NULL,
--     approved    BOOLEAN                                NOT NULL,
--     created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
--     updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
-- );