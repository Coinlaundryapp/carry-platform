#!/usr/bin/env bash
# carry-platform OpenAPI v2 스키마를 docs/api/openapi-v2.json으로 고정한다.
#
# 프론트(carry-app)의 TS 타입 자동 생성(openapi-typescript)이 이 산출물을 소비한다.
# springdoc이 런타임에 노출하는 스키마라 앱을 한 번 띄워야 한다(springdoc-gradle-plugin도
# 내부적으로 앱을 부팅하므로 의존 인프라 요건은 동일 — 단순한 curl 경로를 채택).
#
# 전제: 앱이 구동 중 (예: SPRING_PROFILES_ACTIVE=local ./gradlew :carry-app:bootRun).
# 사용:  ./scripts/export-openapi.sh                 # 기본 http://localhost:8080
#        OPENAPI_URL=http://localhost:8081/api-docs ./scripts/export-openapi.sh
set -euo pipefail
cd "$(dirname "$0")/.."

URL="${OPENAPI_URL:-http://localhost:8080/api-docs}"
OUT="docs/api/openapi-v2.json"

mkdir -p docs/api
curl -sf "$URL" | python -m json.tool > "$OUT"

PATHS=$(python -c "import json; print(len(json.load(open('$OUT'))['paths']))")
echo "✓ $OUT — paths=$PATHS"
