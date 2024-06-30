-- V1__create_user_table.sql
CREATE TABLE users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    phone_number  VARCHAR(14)  NOT NULL,
    commercial_yn TINYINT      NOT NULL,
    location_yn   TINYINT      NOT NULL,
);

CREATE TABLE verification_codes
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(14)  NOT NULL,
    code         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    expired_at   TIMESTAMP    NOT NULL,
);