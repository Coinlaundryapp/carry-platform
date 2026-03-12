#!/bin/bash
# Register the Debezium outbox connector with Kafka Connect
# Prerequisites: docker-compose services (postgres, kafka, kafka-connect) must be running

set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Waiting for Kafka Connect to be ready..."
until curl -s "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  sleep 2
done
echo "Kafka Connect is ready."

echo "Registering carry-outbox-connector..."
curl -s -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d @"${SCRIPT_DIR}/register-connector.json" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "Done. Verify with: curl -s ${CONNECT_URL}/connectors/carry-outbox-connector/status"
