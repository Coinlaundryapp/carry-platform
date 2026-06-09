# carry-platform 로컬 인프라 기동 + Debezium 아웃박스 커넥터 등록 (Windows).
# 사용법:  ./scripts/dev-up.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "▶ 인프라 컨테이너 기동 (postgres·redis·kafka 3-node·connect)..."
docker compose up -d postgres redis kafka-1 kafka-2 kafka-3 kafka-connect

Write-Host "▶ Kafka Connect REST 준비 대기 (http://localhost:8083)..."
do {
    Start-Sleep -Seconds 2
    $ready = $false
    try { $ready = (Invoke-WebRequest -Uri "http://localhost:8083/" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { $ready = $false }
} until ($ready)

Write-Host "▶ Debezium 아웃박스 커넥터 등록..."
# register-connector.sh 는 bash 스크립트 → Git Bash(WSL) 필요.
bash infra/debezium/register-connector.sh

Write-Host "✓ 인프라 준비 완료."
Write-Host '  앱 실행:  $env:MANAGEMENT_TRACING_ENABLED="false"; ./gradlew :carry-app:bootRun'
Write-Host "  (로컬에선 트레이싱 export 를 꺼야 otel exporter 가 요청을 블록하지 않는다)"
