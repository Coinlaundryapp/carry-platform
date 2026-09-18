ALTER TABLE user_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE user_oauth_accounts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES user_users(id),
    provider    VARCHAR(20) NOT NULL,
    oauth_id    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oauth_account UNIQUE (provider, oauth_id)
);
CREATE INDEX idx_oauth_account_user ON user_oauth_accounts(user_id);

INSERT INTO user_oauth_accounts (user_id, provider, oauth_id)
SELECT id, oauth_provider, oauth_id FROM user_users;

ALTER TABLE user_users DROP COLUMN oauth_provider;
ALTER TABLE user_users DROP COLUMN oauth_id;
