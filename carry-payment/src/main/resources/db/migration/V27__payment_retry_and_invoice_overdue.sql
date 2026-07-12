-- payment_invoices.status 는 CHECK 제약 없이 VARCHAR(20)(EnumType.STRING) 로 저장되므로
-- InvoiceStatus 에 OVERDUE 를 추가하는 데 별도 스키마 변경이 필요 없다.
ALTER TABLE payment_payments
    ADD COLUMN retry_count  INT         NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ;

-- 기존 FAILED 결제 백필: NULL 이면 새 스위퍼가 영원히 집어가지 않는다.
-- 재시도 제외(취소 주문 등)는 스위퍼의 인보이스 상태 가드가 담당하므로 무조건 백필.
UPDATE payment_payments SET next_retry_at = NOW() WHERE status = 'FAILED';

CREATE INDEX idx_payments_failed_retry
    ON payment_payments (next_retry_at) WHERE status = 'FAILED';
