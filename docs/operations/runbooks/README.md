# Carry Operations Runbooks

> Phase 3.4(알럿 기준선)에서 정의된 알럿별 대응 절차. Alertmanager가 발신한 알럿의
> `runbook_url` annotation이 가리키는 절대 GitHub URL은 본 디렉토리의 동일 파일명을 따른다.

## 알럿 ↔ 런북 매핑

| Alert | Severity | Route | 런북 |
|---|---|---|---|
| `ApiHighLatencyP99` | warning | slack | [api-p99-latency.md](api-p99-latency.md) |
| `ApiHighErrorRate` | warning | slack | [api-error-rate.md](api-error-rate.md) |
| `KafkaConsumerLag` | warning | slack | [kafka-consumer-lag.md](kafka-consumer-lag.md) |
| `DispatchTimeoutRate` ⚠ | warning | business | [dispatch-timeout-rate.md](dispatch-timeout-rate.md) |
| `PaymentHighFailureRate` | critical | oncall | [payment-failure-rate.md](payment-failure-rate.md) |
| `KafkaDlqNonEmpty` | warning | slack | [kafka-dlq-nonempty.md](kafka-dlq-nonempty.md) |
| `HikariPoolSaturation` | warning | slack | [hikari-pool-saturation.md](hikari-pool-saturation.md) |
| `OrderVolumeDropDoD` | warning | business | [order-volume-drop.md](order-volume-drop.md) |

⚠ `DispatchTimeoutRate`는 `carry_dispatch_timeout_total` 메트릭 부재로 본 PR 시점에서 비활성 (absent 가드). Phase 2 후속 PR로 메트릭 도입 시 자동 활성화.

## 공통 대응 절차 (모든 알럿)

1. **확인** — Alertmanager UI(http://localhost:9093 또는 운영 도메인)에서 firing 상태와 라벨 확인
2. **상관관계** — Saga MDC `correlationId`(Phase 3.3)로 Grafana Logs 패널 검색
3. **메트릭 컨텍스트** — Grafana "Carry — Business & Resilience" 대시보드(http://localhost:3000/d/carry-business)로 직전 1시간 추세
4. **대응** — 알럿별 런북 따름
5. **회고** — 30분 이상 firing 또는 critical 발생 시 ROADMAP 또는 known-debts에 항목 추가 검토

## 라우팅 정책 (Alertmanager)

- `route: slack` — `#carry-alerts` (warning 기본)
- `route: oncall` — PagerDuty/Opsgenie + `#carry-incidents` (critical, 실 연결 후속 PR)
- `route: business` — `#carry-business` (비즈니스 KPI 감지)

`team: carry`, `service: carry-platform` 공통 라벨로 멀티 서비스 환경에서 라우팅 가능.

## 수동 스모크 체크리스트 (로컬)

본 디렉토리의 알럿 인프라가 정상 동작하는지 사람이 확인하는 절차. CI에서는 검증되지 않는다.

1. `docker compose up -d prometheus alertmanager alert-webhook-logger grafana`
2. `http://localhost:9090/rules` → 그룹 `carry-baseline`에 8개 룰
3. `http://localhost:9093/#/status` → Config OK
4. POST `/api/v2/alerts`로 임의 알럿 발신:
   ```powershell
   $a = '[{"labels":{"alertname":"Smoke","severity":"warning","route":"slack"},"annotations":{"summary":"smoke","description":"x","runbook_url":"https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/README.md"}}]'
   Invoke-RestMethod -Method Post -Uri http://localhost:9093/api/v2/alerts -ContentType 'application/json' -Body $a
   ```
5. `docker logs --tail 50 carry-alert-webhook-logger` → POST body 출력 확인
6. Grafana 대시보드 "Active firing alerts" 패널 값 1 이상

## 실 운영 연결 (후속 PR)

- Slack workspace 확보 후 `SLACK_WEBHOOK_URL_CRITICAL`, `SLACK_WEBHOOK_URL_WARNING` 환경변수 주입
- PagerDuty/Opsgenie는 `slack-critical` receiver 옆에 `pagerduty_configs` 추가
- branch protection의 required check에 `validate-alerts` 등록

## 관련 문서

- [Phase 3.4 알럿 기준선 설계](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)
- [Prometheus 룰](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- [Alertmanager 설정](../../../infra/alertmanager/alertmanager.yml)
- [관측 스택 가이드](../../12-observability-stack.md)
- [로깅 정책](../../13-logging-policy.md)
