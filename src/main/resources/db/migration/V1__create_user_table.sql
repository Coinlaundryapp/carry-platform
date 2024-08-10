-- V1__create_user_table.sql
CREATE TABLE users
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100),
    nickname            VARCHAR(100),
    phone_number        VARCHAR(20),
    kakao_id            BIGINT,
    thumbnail_image_url VARCHAR(255),
    profile_image_url   VARCHAR(255),
    connected_at        TIMESTAMP,
    has_email           TINYINT(1),
    is_email_valid      TINYINT(1),
    is_email_verified   TINYINT(1),
    email               VARCHAR(100),
    age_range           VARCHAR(50),
    has_birthday        TINYINT(1),
    birthday            varchar(30),
    birthday_type       varchar(30),
    gender              varchar(30),
    ci                  varchar(255),
    ci_authenticated_at TIMESTAMP
);

CREATE TABLE terms
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_type         ENUM ('MANDATORY', 'OPTIONAL') NOT NULL,
    term_info_title   VARCHAR(100)                   NOT NULL,
    term_info_version INTEGER                        NOT NULL,
    context           TEXT                           NOT NULL,
    created_at        TIMESTAMP                      NOT NULL
);

CREATE TABLE term_agrees
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT     NOT NULL,
    term_id    BIGINT     NOT NULL,
    agree_yn   TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP  NOT NULL
);

CREATE TABLE refresh_tokens
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    value      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);
