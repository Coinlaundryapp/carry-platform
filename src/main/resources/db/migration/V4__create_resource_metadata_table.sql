CREATE TYPE resource_status AS ENUM ('UPLOADING', 'COMPLETE', 'ERROR');

CREATE TABLE resource_metadata
(
    id          BIGSERIAL PRIMARY KEY,
    folder_name VARCHAR(255)                           NOT NULL,
    access_key  uuid                                   NOT NULL,
    extension   VARCHAR(255)                           NOT NULL,
    status      resource_status                        NOT NULL,
    is_valid    BOOLEAN                  DEFAULT FALSE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_resource_metadata_folder_name_access_key ON resource_metadata (folder_name, access_key);
CREATE INDEX idx_resource_metadata_is_valid ON resource_metadata (is_valid);

ALTER TABLE review_media_resources
    RENAME COLUMN media_url TO media_uri;