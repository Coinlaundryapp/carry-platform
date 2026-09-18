#!/usr/bin/env bash
# carry-platform 로컬 Postgres 초기화 — 볼륨을 비우고 빈 DB로 재기동한다.
# 이후 앱(local 프로파일)을 기동하면 Flyway가 V0~V23으로 스키마를 클린 빌드한다.
#
# 로컬 스키마는 Flyway가 관리한다(ddl-auto:validate). 마이그레이션을 추가/수정했거나,
# 과거 ddl-auto:update가 남긴 stale 스키마를 버리고 싶을 때 사용한다.
#
# ⚠️ 로컬 DB 데이터가 모두 삭제된다(dev-up은 시딩하지 않으므로 보통 임시 데이터뿐).
# 사용법: ./scripts/db-reset.sh
set -euo pipefail

cd "$(dirname "$0")/.."

VOLUME="carry-platform_carry-postgres-data"

echo "▶ postgres 컨테이너 정지·삭제..."
docker compose rm -sf postgres

echo "▶ 데이터 볼륨 삭제 ($VOLUME)..."
docker volume rm "$VOLUME" >/dev/null 2>&1 || echo "  (볼륨 없음 — 건너뜀)"

echo "▶ postgres 재기동 (postgis 이미지, 빈 DB)..."
docker compose up -d postgres

echo "✓ 빈 DB 준비 완료. 앱 기동 시 Flyway가 스키마를 빌드한다:"
echo "    SERVER_PORT=8081 SPRING_PROFILES_ACTIVE=local MANAGEMENT_TRACING_ENABLED=false ./gradlew :carry-app:bootRun"
