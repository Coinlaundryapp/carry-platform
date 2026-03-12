CREATE TABLE notification_notifications (
    id                  BIGSERIAL PRIMARY KEY,
    recipient_id        BIGINT NOT NULL,
    recipient_contact   VARCHAR(100) NOT NULL,
    type                VARCHAR(30) NOT NULL,
    channel             VARCHAR(30) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    content             VARCHAR(2000) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        BIGINT,
    sent_at             TIMESTAMPTZ,
    fail_reason         VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_notifications_recipient_id ON notification_notifications(recipient_id);
CREATE INDEX idx_notification_notifications_type ON notification_notifications(type);
CREATE INDEX idx_notification_notifications_status ON notification_notifications(status);
CREATE INDEX idx_notification_notifications_reference ON notification_notifications(reference_type, reference_id);
