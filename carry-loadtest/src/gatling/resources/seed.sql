-- Gatling 부하 시드 데이터.
-- 컬럼 권위: carry-app/src/test/kotlin/com/carry/app/test/TestFixtures.kt
--           + 각 모듈 db/migration (carry-user V2/V14, carry-laundromat V3,
--             carry-service-availability V13, carry-order V5/V15/V17, carry-dispatch V6/V18).
-- 적용 순서: bootRun(local 프로파일, ddl-auto:update)으로 스키마 생성 후 psql로 주입.
-- 멱등성: 재실행 가능하도록 ON CONFLICT DO NOTHING / 사전 정리.

-- ── 정리(재실행 안전) ──
DELETE FROM dispatch_dispatches WHERE area_code = 'GANGNAM';
DELETE FROM order_selected_options WHERE order_id IN (SELECT id FROM orders WHERE customer_id = 1);
DELETE FROM orders WHERE customer_id = 1;

-- ── 사용자: customer(1), carrier(2) ──
INSERT INTO user_users (id, email, name, phone, role, oauth_provider, oauth_id, is_active)
VALUES (1, 'customer1@test.com', '테스트고객1', '010-1234-5678', 'CUSTOMER', 'KAKAO', 'kakao_1', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_users (id, email, name, phone, role, oauth_provider, oauth_id, is_active)
VALUES (2, 'carrier2@test.com', '테스트캐리어2', '010-9876-5432', 'CARRIER', 'KAKAO', 'kakao_carrier_2', true)
ON CONFLICT (id) DO NOTHING;

-- ── 세탁소(1) ──
INSERT INTO laundromat_laundromats (id, name, road_address, latitude, longitude)
VALUES (1, '테스트세탁소', '서울시 강남구 테헤란로 123', 37.5065, 127.0536)
ON CONFLICT (id) DO NOTHING;

-- ── 배송지(1, user 1) ──
INSERT INTO user_shipping_addresses
    (id, user_id, alias, road_address, detail_address, zip_code, latitude, longitude,
     recipient_name, recipient_phone, area_code, is_default)
VALUES (1, 1, '집', '서울시 강남구 테헤란로 123', '4층', '06234', 37.5065, 127.0536,
        '테스트고객', '010-1234-5678', 'GANGNAM', true)
ON CONFLICT (id) DO NOTHING;

-- ── 서비스 영역(GANGNAM) + 7일 전일(00:00~23:59) 운영 스케줄 ──
INSERT INTO service_areas (id, area_code, name, status)
VALUES (1, 'GANGNAM', '강남구', 'ACTIVE')
ON CONFLICT (area_code) DO NOTHING;

INSERT INTO service_area_schedules (service_area_id, day_of_week, open_time, close_time)
SELECT 1, d, TIME '00:00', TIME '23:59'
FROM generate_series(1, 7) AS d
ON CONFLICT (service_area_id, day_of_week) DO NOTHING;

-- ── carrier(2)의 담당 영역(GANGNAM) — available/claim 노출 조건 ──
INSERT INTO dispatch_carrier_areas (carrier_id, area_code, area_name, active)
VALUES (2, 'GANGNAM', '강남구', true)
ON CONFLICT (carrier_id, area_code) DO NOTHING;

-- ── claim 부하용 PENDING 배차 풀: orders N개 + dispatch N개 쌍 ──
-- dispatch.order_id 는 NOT NULL UNIQUE FK → 각 dispatch마다 선행 orders 1행 필요.
-- 풀 크기 30 ≥ claim 시나리오 사용자 20 (Task 10).
WITH new_orders AS (
    INSERT INTO orders
        (customer_id, status, laundromat_id, laundry_item_type,
         road_address, detail_address, zip_code, latitude, longitude,
         recipient_name, recipient_phone, area_code,
         desired_pickup_at, desired_delivery_at, version)
    SELECT
        1, 'CREATED', 1, 'NORMAL',
        '서울시 강남구 테헤란로 123', '4층', '06234', 37.5065, 127.0536,
        '테스트고객', '010-1234-5678', 'GANGNAM',
        now() + interval '2 hours', now() + interval '24 hours', 0
    FROM generate_series(1, 30)
    RETURNING id, laundromat_id, area_code, desired_pickup_at
)
INSERT INTO dispatch_dispatches
    (order_id, laundromat_id, status, carrier_id, area_code, desired_pickup_at, version)
SELECT id, laundromat_id, 'PENDING', NULL, area_code, desired_pickup_at, 0
FROM new_orders;
