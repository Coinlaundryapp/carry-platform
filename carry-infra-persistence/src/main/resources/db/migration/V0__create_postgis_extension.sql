-- PostGIS 확장: 인근 세탁소 검색(ST_Distance·ST_DWithin·::geography, LaundromatJpaRepository)과
-- V3 GIST geography 인덱스의 전제. V3보다 먼저 적용되도록 V0으로 둔다.
-- 모든 환경(local/test/prod)이 이 단일 마이그레이션으로 확장을 자가 프로비저닝한다
-- (postgis 가능 이미지: 로컬·prod는 postgis/postgis, 테스트는 testcontainers postgis/postgis).
CREATE EXTENSION IF NOT EXISTS postgis;
