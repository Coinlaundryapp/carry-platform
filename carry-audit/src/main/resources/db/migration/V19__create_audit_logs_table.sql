-- 민감 운영 작업 감사 로그 (append-only). ROADMAP 4.2.
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 행위 시각(어댑터가 설정)
    actor       VARCHAR(64) NOT NULL,                -- userId 문자열 또는 'SYSTEM'
    role        VARCHAR(32),
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    before      JSONB,
    after       JSONB,
    ip          VARCHAR(64),
    trace_id    VARCHAR(64)
);

CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
