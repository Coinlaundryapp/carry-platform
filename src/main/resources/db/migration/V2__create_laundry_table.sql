-- V2__create_laundry_table.sql
CREATE TYPE laundry_options AS ENUM ('WASHING_MACHINE','DRYER','SNEAKERS');

CREATE TABLE laundries
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100)                           NOT NULL,
    options             laundry_options[]                      NOT NULL,
    address             VARCHAR(255)                           NOT NULL,
    location_coordinate GEOMETRY(POINT, 4326)                  NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE INDEX idx_laundries_location_coordinate ON laundries USING GIST ((location_coordinate::public.geography));