CREATE TABLE IF NOT EXISTS user_infos
(
    id                  BIGINT PRIMARY KEY,
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

-- 기존 UserInfo 데이터 이동
INSERT INTO user_infos (id,
                        thumbnail_image_url,
                        profile_image_url,
                        connected_at,
                        has_email,
                        is_email_valid,
                        is_email_verified,
                        email,
                        age_range,
                        has_birthday,
                        birthday,
                        birthday_type,
                        gender,
                        ci,
                        ci_authenticated_at)
SELECT kakao_id,
       thumbnail_image_url,
       profile_image_url,
       connected_at,
       has_email,
       is_email_valid,
       is_email_verified,
       email,
       age_range,
       has_birthday,
       birthday,
       birthday_type,
       gender,
       ci,
       ci_authenticated_at
FROM users;

ALTER TABLE users
    DROP COLUMN thumbnail_image_url;
ALTER TABLE users
    DROP COLUMN profile_image_url;
ALTER TABLE users
    DROP COLUMN connected_at;
ALTER TABLE users
    DROP COLUMN has_email;
ALTER TABLE users
    DROP COLUMN is_email_valid;
ALTER TABLE users
    DROP COLUMN is_email_verified;
ALTER TABLE users
    DROP COLUMN email;
ALTER TABLE users
    DROP COLUMN age_range;
ALTER TABLE users
    DROP COLUMN has_birthday;
ALTER TABLE users
    DROP COLUMN birthday;
ALTER TABLE users
    DROP COLUMN birthday_type;
ALTER TABLE users
    DROP COLUMN gender;
ALTER TABLE users
    DROP COLUMN ci;
ALTER TABLE users
    DROP COLUMN ci_authenticated_at;

create index idx_user_kakao_id on users (kakao_id);
