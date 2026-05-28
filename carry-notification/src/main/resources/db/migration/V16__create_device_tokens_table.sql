CREATE TABLE notification_device_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_device_tokens_token UNIQUE (token)
);

CREATE INDEX idx_notification_device_tokens_user_id ON notification_device_tokens(user_id);
