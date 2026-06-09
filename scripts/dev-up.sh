#!/usr/bin/env bash
# carry-platform 로컬 인프라 기동 + Debezium 아웃박스 커넥터 등록.
# 사용법: ./scripts/dev-up.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "▶ 인프라 컨테이너 기동 (postgres·redis·kafka 3-node·connect)..."
docker compose up -d postgres redis kafka-1 kafka-2 kafka-3 kafka-connect

echo "▶ Kafka Connect REST 준비 대기 (http://localhost:8083)..."
until curl -sf http://localhost:8083/ >/dev/null 2>&1; do
  sleep 2
done

echo "▶ Debezium 아웃박스 커넥터 등록..."
bash infra/debezium/register-connector.sh

echo "✓ 인프라 준비 완료."
echo "  앱 실행:  MANAGEMENT_TRACING_ENABLED=false ./gradlew :carry-app:bootRun"
echo "  (로컬에선 트레이싱 export 를 꺼야 otel exporter 가 요청을 블록하지 않는다)"
