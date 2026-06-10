-- 낙관적 락: 동시 변경 시 두 번째 커밋의 WHERE id=? AND version=? 매칭 실패 → OptimisticLockingFailureException.
ALTER TABLE payment_payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
