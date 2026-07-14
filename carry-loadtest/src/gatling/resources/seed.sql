-- Gatling 부하 시드 데이터.
-- 컬럼 권위: carry-app/src/test/kotlin/com/carry/app/test/TestFixtures.kt
--           + 각 모듈 db/migration (carry-user V2/V14/V30, carry-laundromat V3,
--             carry-service-availability V13, carry-order V5/V15/V17, carry-dispatch V6/V18).
-- 적용 순서: bootRun(local 프로파일)으로 Flyway가 스키마를 빌드한 뒤 psql로 주입(#143 이후 Flyway 관리).
-- 멱등성: 재실행 가능하도록 ON CONFLICT DO NOTHING / 사전 정리 + 말미 시퀀스 setval.

-- ── 정리(재실행 안전) ──
DELETE FROM dispatch_dispatches WHERE area_code = 'GANGNAM';
DELETE FROM order_selected_options WHERE order_id IN (SELECT id FROM orders WHERE customer_id = 1);
DELETE FROM orders WHERE customer_id = 1;

-- 주의: #143 이후 local도 Flyway로 스키마를 빌드한다(마이그레이션엔 `DEFAULT now()`가 있어
-- created_at/updated_at은 생략 가능하나, 명시 now()를 그대로 둔다 — 무해하고 의도가 명확하다).

-- ── 사용자: customer(1), carrier(2) ──
INSERT INTO user_users (id, email, email_verified, name, phone, role, is_active, created_at, updated_at)
VALUES (1, 'customer1@test.com', false, '테스트고객1', '010-1234-5678', 'CUSTOMER', true, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_users (id, email, email_verified, name, phone, role, is_active, created_at, updated_at)
VALUES (2, 'carrier2@test.com', false, '테스트캐리어2', '010-9876-5432', 'CARRIER', true, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_oauth_accounts (user_id, provider, oauth_id, created_at, updated_at)
VALUES (1, 'KAKAO', 'kakao_1', now(), now())
ON CONFLICT (provider, oauth_id) DO NOTHING;

INSERT INTO user_oauth_accounts (user_id, provider, oauth_id, created_at, updated_at)
VALUES (2, 'KAKAO', 'kakao_carrier_2', now(), now())
ON CONFLICT (provider, oauth_id) DO NOTHING;

-- ── 세탁소(1) ──
INSERT INTO laundromat_laundromats (id, name, road_address, latitude, longitude, created_at, updated_at)
VALUES (1, '테스트세탁소', '서울시 강남구 테헤란로 123', 37.5065, 127.0536, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── 배송지(1, user 1) ──
INSERT INTO user_shipping_addresses
    (id, user_id, alias, road_address, detail_address, zip_code, latitude, longitude,
     recipient_name, recipient_phone, area_code, is_default, created_at, updated_at)
VALUES (1, 1, '집', '서울시 강남구 테헤란로 123', '4층', '06234', 37.5065, 127.0536,
        '테스트고객', '010-1234-5678', 'GANGNAM', true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── 서비스 영역(GANGNAM) + 7일 전일(00:00~23:59) 운영 스케줄 ──
INSERT INTO service_areas (id, area_code, name, status, created_at, updated_at)
VALUES (1, 'GANGNAM', '강남구', 'ACTIVE', now(), now())
ON CONFLICT (area_code) DO NOTHING;

-- service_area_schedules는 Hibernate-DDL에 (service_area_id, day_of_week) UNIQUE가 없어
-- ON CONFLICT 불가 → WHERE NOT EXISTS로 멱등 보장.
INSERT INTO service_area_schedules (service_area_id, day_of_week, open_time, close_time, created_at, updated_at)
SELECT 1, d, TIME '00:00', TIME '23:59', now(), now()
FROM generate_series(1, 7) AS d
WHERE NOT EXISTS (
    SELECT 1 FROM service_area_schedules s WHERE s.service_area_id = 1 AND s.day_of_week = d
);

-- ── carrier(2)의 담당 영역(GANGNAM) — available/claim 노출 조건 ──
INSERT INTO dispatch_carrier_areas (carrier_id, area_code, area_name, active, created_at, updated_at)
VALUES (2, 'GANGNAM', '강남구', true, now(), now())
ON CONFLICT (carrier_id, area_code) DO NOTHING;

-- ── 시퀀스 정합 ──
-- 위 INSERT들은 명시적 PK(id=1,2…)를 주입하지만 BIGSERIAL 시퀀스는 전진하지 않는다.
-- 보정하지 않으면 앱이 nextval로 만든 첫 id들이 시드 id와 충돌해 `duplicate key … _pkey`(500)가 난다
-- (시드 직후 첫 배송지/세탁소 생성 등). 각 시퀀스를 현재 MAX(id)로 맞춰 다음 nextval이 시드 너머에서
-- 시작하게 한다. pg_get_serial_sequence로 시퀀스명에 의존하지 않는다.
SELECT setval(pg_get_serial_sequence('user_users', 'id'), (SELECT MAX(id) FROM user_users));
SELECT setval(pg_get_serial_sequence('laundromat_laundromats', 'id'), (SELECT MAX(id) FROM laundromat_laundromats));
SELECT setval(pg_get_serial_sequence('user_shipping_addresses', 'id'), (SELECT MAX(id) FROM user_shipping_addresses));
SELECT setval(pg_get_serial_sequence('service_areas', 'id'), (SELECT MAX(id) FROM service_areas));

-- ── claim 부하용 PENDING 배차 풀: orders N개 + dispatch N개 쌍 ──
-- dispatch.order_id 는 NOT NULL UNIQUE FK → 각 dispatch마다 선행 orders 1행 필요.
-- 풀 크기 30 ≥ claim 시나리오 사용자 20 (Task 10).
WITH new_orders AS (
    INSERT INTO orders
        (customer_id, status, laundromat_id, laundry_item_type,
         road_address, detail_address, zip_code, latitude, longitude,
         recipient_name, recipient_phone, area_code,
         desired_pickup_at, desired_delivery_at, version, created_at, updated_at)
    SELECT
        1, 'CREATED', 1, 'NORMAL',
        '서울시 강남구 테헤란로 123', '4층', '06234', 37.5065, 127.0536,
        '테스트고객', '010-1234-5678', 'GANGNAM',
        now() + interval '2 hours', now() + interval '24 hours', 0, now(), now()
    FROM generate_series(1, 30)
    RETURNING id, laundromat_id, area_code, desired_pickup_at
)
INSERT INTO dispatch_dispatches
    (order_id, laundromat_id, status, carrier_id, area_code, desired_pickup_at, version, created_at, updated_at)
SELECT id, laundromat_id, 'PENDING', NULL, area_code, desired_pickup_at, 0, now(), now()
FROM new_orders;
