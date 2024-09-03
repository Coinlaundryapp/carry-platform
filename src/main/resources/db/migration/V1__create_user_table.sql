-- V1__create_user_table.sql
CREATE TABLE users
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100),
    nickname            VARCHAR(100),
    phone_number        VARCHAR(20),
    kakao_id            BIGINT,
    thumbnail_image_url VARCHAR(255),
    profile_image_url   VARCHAR(255),
    connected_at        TIMESTAMP,
    has_email           BOOLEAN,
    is_email_valid      BOOLEAN,
    is_email_verified   BOOLEAN,
    email               VARCHAR(100),
    age_range           VARCHAR(50),
    has_birthday        BOOLEAN,
    birthday            VARCHAR(30),
    birthday_type       VARCHAR(30),
    gender              VARCHAR(30),
    ci                  VARCHAR(255),
    ci_authenticated_at TIMESTAMP
);

CREATE
    TYPE term_types AS ENUM ('MANDATORY', 'OPTIONAL');

CREATE TABLE terms
(
    id                BIGSERIAL PRIMARY KEY,
    term_type         term_types   NOT NULL,
    term_info_title   VARCHAR(100) NOT NULL,
    term_info_version INTEGER      NOT NULL,
    context           TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL
);

CREATE TABLE term_agrees
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    term_id    BIGINT    NOT NULL,
    agree_yn   BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE refresh_tokens
(
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT       NOT NULL,
    value     VARCHAR(255) NOT NULL,
    expiry_at TIMESTAMP    NOT NULL
);

CREATE TYPE entrance_types AS ENUM ('PASSWORD', 'FREE_ACCESS', 'SECURITY_CALL', 'HOUSEHOLD_CALL', 'OTHER');

CREATE TABLE shipping_addresses
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT         NOT NULL,
    is_default_address BOOLEAN        NOT NULL,
    address_label      VARCHAR(255)   NOT NULL,
    recipient_name     VARCHAR(255)   NOT NULL,
    recipient_phone    VARCHAR(255)   NOT NULL,
    base_address       VARCHAR(255)   NOT NULL,
    detail_address     VARCHAR(255),
    delivery_notes     VARCHAR(255),
    entrance_type      entrance_types NOT NULL,
    entrance_detail    VARCHAR(255)
);

CREATE
    TYPE notification_types AS ENUM ('ALARM_TALK', 'SMS');

CREATE TABLE service_availability_notifications
(
    id                BIGSERIAL PRIMARY KEY,
    city              VARCHAR(255)       NOT NULL,
    district          VARCHAR(255)       NOT NULL,
    notification_type notification_types NOT NULL,
    contact           VARCHAR(255)       NOT NULL
);