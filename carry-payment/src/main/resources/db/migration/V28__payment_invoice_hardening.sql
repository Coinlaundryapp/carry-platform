-- 인보이스당 결제 1건 불변식. 오늘은 인보이스당 InvoiceIssuedEvent 가 정확히 1회라 안전하지만,
-- 향후 중복 이벤트·경합으로 두 번째 결제가 생성되려는 시도를 DB 레벨에서 깨끗한 롤백으로 전환한다
-- (애플리케이션 멱등 가드 findByOrderId 위의 방어선).
CREATE UNIQUE INDEX uq_payments_invoice ON payment_payments (invoice_id);

-- 인보이스 낙관적 락. 자동과금의 markPaid(ISSUED/OVERDUE→PAID)가 동시 onOrderCancelled 의
-- CANCELLED 쓰기를 덮어써 취소된 인보이스를 PAID 로 되살리는 lost update 를 방지한다 —
-- 두 번째 커밋의 WHERE id=? AND version=? 매칭 실패로 OptimisticLockingFailureException 유발.
ALTER TABLE payment_invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
