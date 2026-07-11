-- PG 대사(reconciliation) 불일치 원장 — 비파괴 감지 기록(자동 보정 없음).
-- (mismatch_type, dedup_key) UNIQUE 로 윈도 중첩 재스캔의 중복 적재를 차단한다.
CREATE TABLE payment_reconciliation_mismatches (
    id                  BIGSERIAL PRIMARY KEY,
    mismatch_type       VARCHAR(30) NOT NULL,
    dedup_key           VARCHAR(300) NOT NULL,
    payment_id          BIGINT,
    order_id            BIGINT,
    pg_transaction_id   VARCHAR(255),
    local_amount        BIGINT,
    pg_amount           BIGINT,
    detail              VARCHAR(500) NOT NULL,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_payment_recon_mismatches_type_dedup
    ON payment_reconciliation_mismatches(mismatch_type, dedup_key);

-- 운영 조회: 미해결 불일치 목록
CREATE INDEX idx_payment_recon_mismatches_unresolved
    ON payment_reconciliation_mismatches(created_at) WHERE resolved_at IS NULL;
