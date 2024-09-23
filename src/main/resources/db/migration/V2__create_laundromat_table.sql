-- V2__create_laundromat_table.sql
CREATE TYPE laundromat_options AS ENUM ('WASHING_MACHINE','DRYER','SNEAKERS');

CREATE TABLE laundromats
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100)                           NOT NULL,
    address             VARCHAR(255)                           NOT NULL,
    location_coordinate GEOGRAPHY(POINT, 4326)                 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundromats_location_coordinate ON laundromats USING GIST (location_coordinate);


CREATE TABLE laundromat_option_mappings
(
    id                BIGSERIAL PRIMARY KEY,
    laundromat_id     BIGINT REFERENCES laundromats (id) ON DELETE CASCADE,
    laundromat_option laundromat_options NOT NULL
);

CREATE INDEX idx_laundromat_options_laundromat_id ON laundromat_option_mappings (laundromat_id);
CREATE INDEX idx_laundromat_options_laundromat_option ON laundromat_option_mappings (laundromat_option);
CREATE UNIQUE INDEX idx_laundromat_options_laundromat_id_laundromat_option ON laundromat_option_mappings (laundromat_id, laundromat_option);

CREATE TABLE laundromat_media_resources
(
    id            BIGSERIAL PRIMARY KEY,
    laundromat_id BIGINT REFERENCES laundromats (id) ON DELETE CASCADE,
    media_url     VARCHAR(255)                           NOT NULL,
    extension     VARCHAR(10)                            NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundromat_media_resources_laundromat_id ON laundromat_media_resources (laundromat_id);
CREATE INDEX idx_laundromat_media_resources_laundromat_id_media_values ON laundromat_media_resources (laundromat_id, media_url, extension);

ALTER TABLE shipping_addresses
    ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE shipping_addresses
    ADD COLUMN longitude DOUBLE PRECISION;

CREATE TABLE reviews
(
    id            BIGSERIAL PRIMARY KEY,
    laundromat_id BIGINT REFERENCES laundromats (id) ON DELETE CASCADE,
    user_id       BIGINT REFERENCES users (id) ON DELETE CASCADE,
    comment       TEXT,
    rating        INTEGER CHECK (rating >= 1 AND rating <= 5) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now()      NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now()      NOT NULL
);

CREATE INDEX idx_reviews_laundromat_id ON reviews (laundromat_id);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_laundromat_id_user_id ON reviews (laundromat_id, user_id);
CREATE INDEX idx_reviews_rating ON reviews (rating);

CREATE TABLE review_media_resources
(
    id        BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES reviews (id) ON DELETE CASCADE,
    media_url VARCHAR(255) NOT NULL,
    extension VARCHAR(10)  NOT NULL
);

CREATE INDEX idx_review_media_resources_review_id ON review_media_resources (review_id);