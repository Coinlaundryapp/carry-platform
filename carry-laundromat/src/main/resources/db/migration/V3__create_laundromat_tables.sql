-- V3__create_laundromat_tables.sql

CREATE TABLE laundromat_laundromats
(
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100)                           NOT NULL,
    road_address   VARCHAR(255)                           NOT NULL,
    detail_address VARCHAR(255),
    zip_code       VARCHAR(10),
    latitude       DOUBLE PRECISION                       NOT NULL,
    longitude      DOUBLE PRECISION                       NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundromat_location
    ON laundromat_laundromats USING GIST (
        ST_MakePoint(longitude, latitude)::geography
    );

CREATE TABLE laundromat_options
(
    laundromat_id BIGINT      NOT NULL REFERENCES laundromat_laundromats (id) ON DELETE CASCADE,
    option        VARCHAR(50) NOT NULL,
    PRIMARY KEY (laundromat_id, option)
);

CREATE TABLE laundromat_media_resources
(
    id            BIGSERIAL PRIMARY KEY,
    laundromat_id BIGINT                                 NOT NULL REFERENCES laundromat_laundromats (id) ON DELETE CASCADE,
    media_url     VARCHAR(500)                           NOT NULL,
    extension     VARCHAR(10)                            NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundromat_media_laundromat_id
    ON laundromat_media_resources (laundromat_id);
