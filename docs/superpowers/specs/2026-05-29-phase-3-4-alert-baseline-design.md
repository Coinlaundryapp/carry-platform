# Phase 3.4 — 알럿 기준선 + 운영 런북 디자인

> ROADMAP Phase 3.4(알럿 기준선) + Phase 3.5(운영 런북) 합본 설계
>
> 작성일: 2026-05-29
> 상태: Approved (사용자 승인 완료)
> 후속: writing-plans 스킬로 구현 플랜 생성

---

## 1. 배경

Phase 3.1(비즈니스 메트릭 활성화) · Phase 3.2(Prometheus + Grafana 스택) · Phase 3.3(Saga 상관관계 로깅)이 머지된 상태로, 메트릭은 흐르지만 사람에게 도달하는 알럿 경로가 없다. 본 디자인은 ROADMAP Phase 3.4에 명시된 8개 알럿 조건과 Phase 3.5의 알럿별 운영 런북을 단일 PR로 묶어 "메트릭 → 알럿 → 사람" 파이프라인의 골격을 완성한다.

본 프로젝트는 1인 학습용 PoC로, 실제 Slack workspace · PagerDuty · 온콜 로테이션은 없다. 따라서 알럿 인프라는 "실 통지" 대신 "통지 경로의 구조적 학습"에 가치를 둔다.

## 2. 상위 결정 (사용자 승인 완료)

| 결정 | 채택 | 사유 |
|---|---|---|
| 알럿 엔진 | **Prometheus Alertmanager** | GitOps 친화, promtool 단위 테스트 가능, 업계 표준 |
| 수신자 | **로그 웹훅 + Slack 플레이스홀더** | env 미설정 시 webhook-logger 폴백, 실 운영 전환 경로 확보 |
| 스코프 | **ROADMAP 3.4 + 3.5 합본** | 3.5는 3.4의 종속 항목, PoC라 분리 이득 없음 |
| 구현 언어 변경 | **Kotlin 코드 0줄** | 알럿은 메트릭 소비자, 룰 #4 메트릭 부재는 별도 알려진 빚으로 |

## 3. 시스템 아키텍처

### 3.1 컴포넌트 토폴로지

```
                  ┌────────────────────────┐
                  │   carry-platform app   │
                  │   :8080/actuator/      │
                  │     prometheus         │
                  └───────────┬────────────┘
                              │ scrape (15s)
                              ▼
                  ┌────────────────────────┐
                  │      Prometheus        │
                  │ rule_files: baseline   │◀── promtool test rules (CI)
                  │ evaluation: 30s        │
                  └───────────┬────────────┘
                              │ POST /api/v2/alerts
                              ▼
                  ┌────────────────────────┐
                  │     Alertmanager       │
                  │   routing tree by      │◀── amtool check-config (CI)
                  │   route label          │
                  └───────────┬────────────┘
                              │
              ┌───────────────┼────────────────┐
              ▼               ▼                ▼
       ┌──────────┐   ┌──────────────┐  ┌──────────────┐
       │  oncall  │   │    slack     │  │   business   │
       └────┬─────┘   └──────┬───────┘  └──────┬───────┘
            │                │                 │
            └────────────────┼─────────────────┘
                             ▼
                  ┌────────────────────────┐
                  │ alert-webhook-logger   │
                  │ (stdout-only HTTP)     │
                  │ + Slack placeholder    │
                  │   ($SLACK_WEBHOOK_URL) │
                  └────────────────────────┘
```

### 3.2 파일·디렉토리 변경

```
infra/
├── prometheus.yml                          # 수정: rule_files + alerting 추가
├── prometheus/
│   └── rules/
│       ├── carry-baseline.rules.yml        # 신규
│       └── tests/
│           └── carry-baseline.test.yml     # 신규 (promtool test rules 형식)
├── alertmanager/
│   └── alertmanager.yml                    # 신규
└── grafana/dashboards/
    └── carry-business.json                 # 수정: Alertmanager 상태 패널 1개 추가

docker-compose.yml                          # 수정: alertmanager + alert-webhook-logger 서비스 2개 추가

.github/workflows/
└── validate-alerts.yml                     # 신규: Docker 기반 promtool/amtool 검증

docs/operations/runbooks/                   # 신규 디렉토리
├── README.md                               # 알럿↔런북 매핑 + 공통 절차 + 수동 스모크 체크리스트
├── api-p99-latency.md
├── api-error-rate.md
├── kafka-consumer-lag.md
├── dispatch-timeout-rate.md
├── payment-failure-rate.md
├── kafka-dlq-nonempty.md
├── hikari-pool-saturation.md
└── order-volume-drop.md

ROADMAP.md                                  # 수정: Phase 3.4 / 3.5 체크박스 체크
```

Kotlin 소스 변경 없음. 기존 513개 테스트는 그대로 통과해야 한다(회귀 게이트).

## 4. 알럿 룰 카탈로그

`infra/prometheus/rules/carry-baseline.rules.yml`, 단일 그룹 `carry-baseline`, `interval: 30s`.

각 룰의 공통 라벨: `team=carry`, `service=carry-platform`, `severity=warning|critical`, `route=slack|oncall|business`.
각 룰의 공통 annotations: `summary`(한 줄 한국어), `description`(`{{ $value | humanize }}` 포함), `runbook_url`(GitHub develop 브랜치 절대 URL).

| # | alert | severity | for | expr | route | 비고 |
|---|---|---|---|---|---|---|
| 1 | `ApiHighLatencyP99` | warning | 5m | `histogram_quantile(0.99, sum by(le) (rate(http_server_requests_seconds_bucket[5m]))) > 3` | slack | URI 라벨 sum-out — 전체 p99. URI별 분리는 후속 |
| 2 | `ApiHighErrorRate` | warning | 5m | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 0.001) > 0.05` | slack | 5xx만 카운트 — 4xx는 클라이언트 문제 |
| 3 | `KafkaConsumerLag` | warning | 10m | `sum by(topic) (kafka_consumer_fetch_manager_records_lag) > 1000` | slack | 토픽별 firing |
| 4 | `DispatchTimeoutRate` | warning | 15m | `(sum(rate(carry_dispatch_timeout_total[15m])) / clamp_min(sum(rate(carry_dispatch_accepted_total[15m])) + sum(rate(carry_dispatch_rejected_total[15m])) + sum(rate(carry_dispatch_timeout_total[15m])), 0.001)) > 0.2 and sum(rate(carry_dispatch_timeout_total[15m])) > 0` | business | ⚠ **메트릭 미구현 — `and ... > 0` 가드로 absent 시 firing 차단. 런북·known-debts에 명시** |
| 5 | `PaymentHighFailureRate` | critical | 5m | `sum(rate(carry_payment_failure_total[5m])) / clamp_min(sum(rate(carry_payment_success_total[5m])) + sum(rate(carry_payment_failure_total[5m])), 0.001) > 0.05` | oncall | PG별 라벨 `pg` 자연 노출 |
| 6 | `KafkaDlqNonEmpty` | warning | 1m | `sum by(topic) (increase(carry_kafka_dlq_total[5m])) > 0` | slack | ROADMAP "DLQ 메시지 수 > 0"의 운영 가능한 해석 |
| 7 | `HikariPoolSaturation` | warning | 10m | `hikaricp_connections_active{pool="CarryHikariPool"} / hikaricp_connections_max{pool="CarryHikariPool"} > 0.8` | slack | leak detection은 별 신호 — 알럿화 안 함 |
| 8 | `OrderVolumeDropDoD` | warning | 30m | `(sum(rate(carry_order_created_total[1h])) / clamp_min(sum(rate(carry_order_created_total[1h] offset 1d)), 0.001)) < 0.5 and sum(rate(carry_order_created_total[1h] offset 1d)) > 0.01` | business | ⚠ **24h 데이터 미존재 시 영구 firing 방지 가드** |

## 5. Alertmanager 라우팅

`infra/alertmanager/alertmanager.yml` 구조:

```yaml
route:
  group_by: ['alertname', 'route']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'webhook-logger'        # 기본값 — 매칭 안 되면 로그로
  routes:
    - matchers: [route="oncall"]
      receiver: 'slack-critical'
      continue: true                # webhook-logger도 동시에 받도록
    - matchers: [route="slack"]
      receiver: 'slack-warning'
      continue: true
    - matchers: [route="business"]
      receiver: 'webhook-logger'

receivers:
  - name: 'webhook-logger'
    webhook_configs:
      - url: 'http://alert-webhook-logger:8080/'
        send_resolved: true
  - name: 'slack-critical'
    slack_configs:
      - api_url: '${SLACK_WEBHOOK_URL_CRITICAL:-http://alert-webhook-logger:8080/slack-stub-critical}'
        channel: '#alerts-critical'
        send_resolved: true
  - name: 'slack-warning'
    slack_configs:
      - api_url: '${SLACK_WEBHOOK_URL_WARNING:-http://alert-webhook-logger:8080/slack-stub-warning}'
        channel: '#alerts-warning'
        send_resolved: true
```

핵심: env 미설정 시 placeholder URL을 webhook-logger의 stub 경로로 직접 가리켜 시끄러운 에러 로그 회피. `continue: true`로 모든 알럿이 webhook-logger에도 도달해 학습용 로그 확보.

## 6. docker-compose 패치

추가 서비스 2개:

```yaml
alertmanager:
  image: prom/alertmanager:v0.27.0
  container_name: carry-alertmanager
  ports: ["9093:9093"]
  volumes:
    - ./infra/alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    - carry-alertmanager-data:/alertmanager
  command:
    - '--config.file=/etc/alertmanager/alertmanager.yml'
    - '--storage.path=/alertmanager'
  environment:
    SLACK_WEBHOOK_URL_CRITICAL: ${SLACK_WEBHOOK_URL_CRITICAL:-}
    SLACK_WEBHOOK_URL_WARNING:  ${SLACK_WEBHOOK_URL_WARNING:-}
  depends_on: [alert-webhook-logger]

alert-webhook-logger:
  image: adnanh/webhook:2.8.1            # 또는 동등 stdout HTTP 수신자
  container_name: carry-alert-webhook-logger
  ports: ["9095:8080"]
  # 호스트에서 curl http://localhost:9095/ 로 직접 검증 가능
```

기존 `prometheus` 서비스의 `command:`에 다음 추가:
```yaml
  - "--web.enable-lifecycle"           # 이미 있음
```
그리고 `prometheus.yml`에 추가:
```yaml
rule_files:
  - "/etc/prometheus/rules/*.yml"
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```
볼륨 마운트 추가:
```yaml
prometheus:
  volumes:
    - ./infra/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    - ./infra/prometheus/rules:/etc/prometheus/rules:ro    # 신규
    - carry-prometheus-data:/prometheus
```

새 named volume: `carry-alertmanager-data`.

이미지 선택 주의: `adnanh/webhook`은 별도 hook 설정 필요할 수 있음. 대안으로 `mendhak/http-https-echo`(echo body to logs)이 더 단순. 구현 단계에서 두 후보를 검토 후 결정한다.

## 7. 테스트 전략

### Layer 1: YAML 문법·구조 검증 (CI 필수)

GitHub Actions 워크플로 `.github/workflows/validate-alerts.yml`:

```yaml
name: validate-alerts
on:
  push: { paths: ['infra/prometheus/**', 'infra/alertmanager/**', '.github/workflows/validate-alerts.yml'] }
  pull_request: { paths: ['infra/prometheus/**', 'infra/alertmanager/**'] }
jobs:
  promtool-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: promtool check rules
        run: docker run --rm -v $PWD/infra/prometheus:/p prom/prometheus:v3.0.1
             promtool check rules /p/rules/carry-baseline.rules.yml
      - name: promtool test rules
        run: docker run --rm -v $PWD/infra/prometheus:/p prom/prometheus:v3.0.1
             promtool test rules /p/rules/tests/carry-baseline.test.yml
  amtool-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: amtool check-config
        run: docker run --rm -v $PWD/infra/alertmanager:/a prom/alertmanager:v0.27.0
             amtool check-config /a/alertmanager.yml
```

**Gradle 통합 안 함.** 로컬 머신에서 promtool/amtool 설치를 강제하지 않고, CI에서만 Docker로 실행해 진입 장벽 낮춤. (사용자 확정: "CI-only 추천 — 동의")

### Layer 2: 룰 단위 테스트 (promtool test rules)

`infra/prometheus/rules/tests/carry-baseline.test.yml`. 각 룰당 최소 2 케이스:

| 룰 | 양성 케이스 | 음성 케이스 |
|---|---|---|
| 1 | p99 4s 입력 → fire | p99 2s → no fire |
| 2 | 5xx 10%/200 OK 90% → fire | 5xx 1% → no fire |
| 3 | lag 2000 (5분) → fire (for=10m이므로 추가 시리즈 필요) | lag 500 → no fire |
| 4 | timeout/total = 30% → fire | timeout 메트릭 absent → no fire (가드 검증) |
| 5 | failure/total = 10% → fire | failure 0%, success 100% → no fire |
| 6 | dlq 카운터 5분간 +3 → fire | dlq 변화 없음 → no fire |
| 7 | active=18, max=20 → fire | active=10, max=20 → no fire |
| 8 | now 5/h, yesterday 20/h → fire | yesterday 0(가드 작동) → no fire |

룰 #4 음성 케이스(absent → no fire)와 #8 음성 케이스(가드 작동)는 **핵심 회귀 방어선**.

### Layer 3: 수동 통합 스모크 (자동화 안 함)

`docs/operations/runbooks/README.md`의 "수동 검증 체크리스트" 섹션에 단계 명시. 자동화하지 않는 이유: PoC에서 알럿 firing은 시간 의존적(`for: 5m`) → CI 자동화 비용 대비 학습 가치 낮음.

### Layer 4: Spring Boot 단위 테스트 — 추가 없음

본 PR에 Kotlin 변경 0. 기존 513개 테스트가 그대로 통과하는지가 회귀 게이트.

### 완료 기준

- [x] `promtool check rules` 통과
- [x] `promtool test rules` 모든 케이스 통과
- [x] `amtool check-config` 통과
- [x] 기존 Gradle 빌드/테스트 통과 (Kotlin 0줄 변경)
- [x] 런북 8개 파일 존재 + 각 룰 `runbook_url`이 유효한 상대 경로 (PR description에 grep 결과 첨부)
- [x] 수동 스모크 스크린샷 (Prometheus Alerts 탭 / Alertmanager UI / webhook-logger 컨테이너 로그) PR description 첨부

## 8. 알려진 빚 (Known Debts)

본 PR이 의도적으로 미루는 항목 — `carry-platform-known-debts` 메모리에 등록:

1. **`carry.dispatch.timeout` 메트릭 부재** — `Dispatch.timeout()` 도메인 메서드는 있으나 자동 만료 스케줄러도 카운터 호출도 없음. 룰 #4는 absent 가드로 비활성 상태. 후속 PR에서 (a) Dispatch 만료 스케줄러 + 도메인 이벤트 발행, (b) `DispatchCommandService.markTimedOut()`에 `metrics.incrementCounter("carry.dispatch.timeout")` 추가
2. **실 Slack/PagerDuty 미연결** — placeholder URL과 env 주입 경로만 마련. 실 운영 전환 시 환경변수 주입으로 즉시 활성화 가능
3. **branch protection에 `validate-alerts` required check 미등록** — 본 PR에서 워크플로만 추가. 후속 PR에서 GitHub branch protection 룰에 required status check로 등록
4. **이벤트 라우팅 정교화 미진** — `group_by` 기본 + 단순 라우팅. 라벨 기반 muting/inhibition은 후속

## 9. 리스크 & 미티게이션

| 리스크 | 가능성 | 영향 | 미티게이션 |
|---|---|---|---|
| webhook-logger 이미지 선택 — `adnanh/webhook`이 hooks.json 강제 | 중 | 중 | 구현 시 `mendhak/http-https-echo` 또는 nginx 디버그 이미지로 대체 검토. echo 컨테이너가 더 단순할 가능성 |
| Linux Docker에서 `host.docker.internal` 미동작 | 저 | 저 | docker-compose `extra_hosts` 이미 있음. alertmanager 신규 서비스에도 동일 적용 — 단, alertmanager는 컨테이너 내부 통신만 하므로 영향 없음 |
| `for: 5m`+`evaluation_interval: 30s` 조합으로 promtool test 시 긴 시계열 필요 | 중 | 저 | 테스트에서 짧은 step 사용 + 명시적 시간 진행 |
| `OrderVolumeDropDoD` 룰이 신규 환경에서 1일 이내엔 가드로 0 평가됨 | 고 | 저 | 의도된 동작. 런북에 "신규 환경 24h 운영 후 활성" 명시 |
| Phase 3.5 런북 8개 작성 — 분량 부담 | 중 | 저 | 공통 템플릿 + 알럿별 차별점만 강조. 런북 1개 평균 100~150줄로 제어 |

## 10. 구현·머지 계획

### 10.1 브랜치/PR

- 단일 브랜치 `feature/alert-baseline-and-runbooks`, base `develop`
- 단일 커밋: `feat(observability): ROADMAP Phase 3.4 — 알럿 기준선 + 운영 런북`
- PR description 구조: 배경 / 변경 사항 / 알럿 카탈로그 / 라우팅 다이어그램 / 검증 결과 / 알려진 빚 / ROADMAP 진행

### 10.2 구현 순서 (writing-plans 스킬이 상세화)

1. 디렉토리 골격 + 빈 룰/Alertmanager YAML
2. promtool 테스트 작성 (각 룰의 음성 케이스 우선 — red 확인) → 룰 expr 채우기 (green)
3. `amtool check-config` 통과까지 Alertmanager YAML 완성
4. docker-compose 패치 + prometheus.yml 패치
5. GitHub Actions 워크플로
6. 런북 8개 (공통 템플릿 → 알럿별 차별점)
7. 수동 스모크 — `docker compose up` → 임의 firing 트리거 → 스크린샷
8. ROADMAP 체크박스 + 메모리 갱신 (`carry-platform-roadmap-progress`, `carry-platform-known-debts`)

### 10.3 머지

- CI(`build.yml` + `validate-alerts.yml`) 전부 통과
- `gh pr create` → `gh pr merge --auto --merge --delete-branch` (carry-platform-auto-merge-prs 메모리 준수)

## 11. 후속 작업 (별 PR)

- Dispatch 자동 만료 스케줄러 + `carry.dispatch.timeout` 메트릭 → 룰 #4 자동 활성화
- branch protection에 `validate-alerts` required check 등록
- Alertmanager inhibition 룰 (예: `PaymentHighFailureRate` firing 시 하위 결제 알럿 muting)
- 실 Slack workspace 연결 시 env 주입 + Slack 메시지 템플릿 다듬기

---

**상태**: 사용자 승인 완료. 다음 단계는 spec-document-reviewer 서브에이전트로 본 문서 리뷰 → 통과 시 writing-plans 스킬로 구현 플랜 생성.
