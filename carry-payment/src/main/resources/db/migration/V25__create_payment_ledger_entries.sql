-- 정산 원장(append-only 머니무브먼트) — 정산 근거가 "재계산"이 아닌 "기록"이 되도록.
-- 거래 그룹(결제/환불)당 Σamount = 0 균형 기입, 환불은 역분개 행 추가(UPDATE/DELETE 없음).
-- 분배 모델(2026-07-12 확정): 세탁비·배달비 → CARRIER(코인세탁소 기계 현금 투입 변제 + 배달
-- 수고비), 수수료 → PLATFORM. 세탁소는 정산 수취인이 아니다.
CREATE TABLE payment_ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      BIGINT NOT NULL,
    order_id        BIGINT NOT NULL,
    entry_type      VARCHAR(20) NOT NULL,
    account_type    VARCHAR(20) NOT NULL,
    account_id      BIGINT,
    charge_type     VARCHAR(30),
    amount          BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 계정 잔액 집계(SUM) 경로
CREATE INDEX idx_payment_ledger_account ON payment_ledger_entries(account_type, account_id);
-- 거래 추적 경로
CREATE INDEX idx_payment_ledger_payment_id ON payment_ledger_entries(payment_id);
CREATE INDEX idx_payment_ledger_order_id ON payment_ledger_entries(order_id);
