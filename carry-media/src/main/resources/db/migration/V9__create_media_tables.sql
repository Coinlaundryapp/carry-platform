CREATE TABLE media_resources (
    id              BIGSERIAL       PRIMARY KEY,
    folder          VARCHAR(50)     NOT NULL,
    access_key      UUID            NOT NULL UNIQUE,
    original_filename VARCHAR(255)  NOT NULL,
    extension       VARCHAR(20)     NOT NULL,
    content_type    VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    file_size       BIGINT,
    uploaded_by     BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_media_resources_folder ON media_resources (folder);
CREATE INDEX idx_media_resources_uploaded_by ON media_resources (uploaded_by);
