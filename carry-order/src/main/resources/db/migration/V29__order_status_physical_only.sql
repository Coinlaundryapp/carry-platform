-- 결제 상태를 주문에서 추방 — 물리 상태로 매핑 (실데이터 없는 학습 프로젝트, dev 데이터만 해당)
-- INVOICED/PAYMENT_FAILED/PAID 는 모두 "수거는 됐으나 세탁 미시작"이므로 물리적으로 PICKED_UP 이다.
-- (IN_PROGRESS 는 LaundryStartedEvent 이후 상태 — PAID 를 IN_PROGRESS 로 매핑하면 이후 세탁 시작
--  이벤트가 IN_PROGRESS→IN_PROGRESS 무효 전이로 poison 이 된다.)
UPDATE orders SET status = 'PICKED_UP' WHERE status IN ('INVOICED', 'PAYMENT_FAILED', 'PAID');
UPDATE orders SET status = 'CANCELLED' WHERE status IN ('REFUND_PENDING', 'REFUNDED');
ALTER TABLE orders DROP COLUMN IF EXISTS invoice_id;
ALTER TABLE orders DROP COLUMN IF EXISTS total_amount;
