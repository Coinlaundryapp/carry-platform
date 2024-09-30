CREATE TYPE resource_status AS ENUM ('UPLOADING', 'COMPLETE', 'ERROR');

CREATE TABLE resource_metadata
(
    id          uuid PRIMARY KEY,
    folder_name VARCHAR(255)                           NOT NULL,
    extension   VARCHAR(255)                           NOT NULL,
    status      resource_status                        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);
