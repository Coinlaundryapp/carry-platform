-- 결제 상태를 주문에서 추방 — 물리 상태로 매핑 (실데이터 없는 학습 프로젝트, dev 데이터만 해당)
UPDATE orders SET status = 'PICKED_UP'   WHERE status IN ('INVOICED', 'PAYMENT_FAILED');
UPDATE orders SET status = 'IN_PROGRESS' WHERE status = 'PAID';
UPDATE orders SET status = 'CANCELLED'   WHERE status IN ('REFUND_PENDING', 'REFUNDED');
ALTER TABLE orders DROP COLUMN IF EXISTS invoice_id;
ALTER TABLE orders DROP COLUMN IF EXISTS total_amount;
