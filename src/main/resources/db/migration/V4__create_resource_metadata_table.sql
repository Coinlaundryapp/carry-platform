CREATE TYPE resource_status AS ENUM ('UPLOADING', 'COMPLETE', 'ERROR');

CREATE TABLE resource_metadata
(
    id         uuid PRIMARY KEY,
    filename   VARCHAR(255)                           NOT NULL,
    status     resource_status                        NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_resource_metadata_id_filename ON resource_metadata (id, filename);