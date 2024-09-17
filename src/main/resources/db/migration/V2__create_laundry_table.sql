-- V2__create_laundry_table.sql
CREATE TYPE laundry_options AS ENUM ('WASHING_MACHINE','DRYER','SNEAKERS');

CREATE TABLE laundries
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100)                           NOT NULL,
    address             VARCHAR(255)                           NOT NULL,
    location_coordinate GEOGRAPHY(POINT, 4326)                 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundries_location_coordinate ON laundries USING GIST (location_coordinate);


CREATE TABLE laundry_option_mappings
(
    id             BIGSERIAL PRIMARY KEY,
    laundry_id     BIGINT REFERENCES laundries (id) ON DELETE CASCADE,
    laundry_option laundry_options NOT NULL
);

CREATE INDEX idx_laundry_options_laundry_id ON laundry_option_mappings (laundry_id);
CREATE INDEX idx_laundry_options_laundry_option ON laundry_option_mappings (laundry_option);
CREATE UNIQUE INDEX idx_laundry_options_laundry_id_laundry_option ON laundry_option_mappings (laundry_id, laundry_option);

CREATE TABLE laundry_images
(
    id         BIGSERIAL PRIMARY KEY,
    laundry_id BIGINT REFERENCES laundries (id) ON DELETE CASCADE,
    image_url  VARCHAR(255)                           NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundry_images_laundry_id ON laundry_images (laundry_id);
CREATE INDEX idx_laundry_images_laundry_id_image_url ON laundry_images (laundry_id, image_url);

ALTER TABLE shipping_addresses
    ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE shipping_addresses
    ADD COLUMN longitude DOUBLE PRECISION;

CREATE TABLE reviews
(
    id         BIGSERIAL PRIMARY KEY,
    laundry_id BIGINT REFERENCES laundries (id) ON DELETE CASCADE,
    user_id    BIGINT REFERENCES users (id) ON DELETE CASCADE,
    comment    TEXT,
    rating     INTEGER CHECK (rating >= 1 AND rating <= 5) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()      NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()      NOT NULL
);

CREATE INDEX idx_reviews_laundry_id ON reviews (laundry_id);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_laundry_id_user_id ON reviews (laundry_id, user_id);
CREATE INDEX idx_reviews_rating ON reviews (rating);

CREATE TABLE review_images
(
    id        BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES reviews (id) ON DELETE CASCADE,
    image_url VARCHAR(255) NOT NULL
);

CREATE INDEX idx_review_images_review_id ON review_images (review_id);