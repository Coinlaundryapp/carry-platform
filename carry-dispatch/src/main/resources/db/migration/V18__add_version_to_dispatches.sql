-- Optimistic locking 카운터 추가 (ROADMAP Phase 2.5 동시성 보장).
-- 두 캐리어가 동일 PENDING 배차를 동시 claim 시 두 번째 트랜잭션 commit에서
-- WHERE id=? AND version=? 매칭 실패 → OptimisticLockingFailureException.
ALTER TABLE dispatch_dispatches ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
