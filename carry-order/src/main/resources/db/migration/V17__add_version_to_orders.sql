-- Optimistic locking 카운터 추가 (ROADMAP Phase 2.5 동시성 보장).
-- 기존 row는 0으로 시작하며 이후 UPDATE마다 Hibernate가 자동으로 증가시킨다.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
