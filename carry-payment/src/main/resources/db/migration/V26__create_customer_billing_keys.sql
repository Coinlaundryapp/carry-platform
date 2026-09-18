-- 고객 자동결제 수단. billing_key 는 애플리케이션 레벨 AES-GCM 암호문(Base64) 저장.
-- customer_key 는 PG 전달용 랜덤 식별자 — DB PK 재사용 금지 정책의 물리화.
CREATE TABLE customer_billing_keys (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL,
    customer_key    VARCHAR(64) NOT NULL,
    billing_key     VARCHAR(512) NOT NULL,
    card_company    VARCHAR(50) NOT NULL,
    card_last4      VARCHAR(4) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    invalidated_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 고객당 활성 키 1개 불변식
CREATE UNIQUE INDEX uq_billing_keys_active_per_customer
    ON customer_billing_keys (customer_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_billing_keys_customer ON customer_billing_keys (customer_id);
