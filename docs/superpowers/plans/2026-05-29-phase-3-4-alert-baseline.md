# Phase 3.4 Alert Baseline + Runbooks Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** `docs/superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md`

**Goal:** ROADMAP Phase 3.4(알럿 기준선)와 3.5(운영 런북)를 단일 PR로 머지 — Prometheus alert rules 8개, Alertmanager 라우팅, GitHub Actions 검증 워크플로, 알럿별 런북 8개를 추가하고 기존 513개 Kotlin 테스트 회귀 없이 통과.

**Architecture:** `prom/prometheus:v3.0.1`이 `/etc/prometheus/rules/*.rules.yml`에서 8개 룰을 로드하고 평가 → Alertmanager(`prom/alertmanager:v0.27.0`)에 firing 신호 전달 → `route` 라벨 기반 라우팅(`oncall|slack|business`)으로 Slack placeholder 수신자 및 `alert-webhook-logger`(stdout HTTP echo)에 전달. Kotlin 소스 변경 0. promtool/amtool 검증은 GitHub Actions Docker 실행.

**Tech Stack:** Prometheus 3.0.1, Alertmanager 0.27.0, Grafana OSS 11.4.0, Docker Compose, GitHub Actions, mendhak/http-https-echo 또는 동등 stdout HTTP 수신자.

**Branch:** `feature/alert-baseline-and-runbooks` (base: `develop`, 이미 생성됨, 스펙 커밋 2개 포함)

---

## 파일 구조

| 파일 | 책임 | 상태 |
|---|---|---|
| `infra/prometheus.yml` | scrape + rule_files 글롭 + alertmanagers 타깃 | 수정 |
| `infra/prometheus/rules/carry-baseline.rules.yml` | 8개 알럿 룰 정의 (단일 그룹) | 신규 |
| `infra/prometheus/rules/tests/carry-baseline.test.yml` | promtool test rules: 8 룰 × 2 케이스 | 신규 |
| `infra/alertmanager/alertmanager.yml` | 라우팅 트리 + 3 receiver | 신규 |
| `docker-compose.yml` | alertmanager + alert-webhook-logger 서비스 + Prometheus rules 볼륨 | 수정 |
| `infra/grafana/dashboards/carry-business.json` | "Active firing alerts" 패널 1개 | 수정 |
| `.github/workflows/validate-alerts.yml` | promtool check/test + amtool check | 신규 |
| `docs/operations/runbooks/README.md` | 알럿↔런북 매핑 + 공통 절차 + 수동 스모크 체크리스트 | 신규 |
| `docs/operations/runbooks/{8개}.md` | 알럿별 대응 절차 | 신규 |
| `ROADMAP.md` | Phase 3.4·3.5 체크박스 갱신 | 수정 |
| `MEMORY.md`, `carry-platform-roadmap-progress.md`, `carry-platform-known-debts.md` | 메모리 갱신 | 수정 |

---

## 청크 분할

1. **Chunk 1**: 알럿 룰 + promtool 테스트 + Alertmanager config (TDD red→green 사이클)
2. **Chunk 2**: docker-compose/prometheus.yml 인프라 통합 + Grafana 패널
3. **Chunk 3**: CI 워크플로 + 8개 런북 + 수동 스모크 + ROADMAP/메모리 갱신 + PR

각 청크 완료 시 plan-document-reviewer 디스패치 → 통과 후 실행 단계로.

---

## Chunk 1: 알럿 룰 + promtool 테스트 + Alertmanager config

각 룰을 TDD로 추가: 음성 케이스 먼저(absent → no fire) → 양성 케이스(threshold 초과 → fire) → `promtool test rules` 실패 확인 → 룰 expr 채워 통과.

### promtool 시계열 표기 규칙 (모든 테스트 공통)

- 형식 `'A+Bxc'` = 시작 A, 매 step +B, **c+1개** 샘플 생성
- **카운터 메트릭** (`*_total`, `http_server_requests_seconds_*`, `carry_kafka_dlq_total`)은 `rate()`/`increase()`로 평가되므로 **반드시 증가 형태**여야 함. 평탄한 시리즈(`'5x20'`)는 rate=0이 되어 양성 케이스가 firing하지 못한다.
- **게이지 메트릭** (`hikaricp_connections_*`, `kafka_consumer_fetch_manager_records_lag`)은 직접 비교되므로 평탄한 시리즈 OK
- **`exp_annotations` 처리** (Chunk 1 실행 중 발견 — 플랜 사후 수정): promtool v3.0.1은 `exp_alerts`를 명시한 양성 케이스에서 annotations 비교를 **강제**한다(생략 옵션 없음). 따라서 양성 테스트마다 **rendered 값을 정확히 기록**해야 한다 — `humanizePercentage`는 `'10%'` 형태, KafkaDlqNonEmpty의 `5분 증분={{ $value }}건`은 `'5분 증분=2.2222222222222223건'`처럼 부동소수점 그대로. 룰 annotation 표현식을 바꾸면 테스트 expectation도 재기록해야 함(브리틀 ↑). 음성 케이스는 `exp_alerts`를 생략하므로 영향 없음.

- **docker 실행 시 `--entrypoint` 오버라이드 필수** (Chunk 1 실행 중 발견): `prom/prometheus:v3.0.1`과 `prom/alertmanager:v0.27.0` 이미지의 `ENTRYPOINT`는 서비스 바이너리(`prometheus`/`alertmanager`)다. promtool/amtool을 호출하려면 반드시 `--entrypoint promtool` / `--entrypoint amtool` 옵션을 줘야 한다.
  - 예: `docker run --rm --entrypoint promtool -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 check rules /p/rules/carry-baseline.rules.yml`
  - Chunk 1 안의 docker 명령 예시는 사후 안내가 부족하지만, Chunk 2·3에서 이 패턴을 따른다.

### Task 1.1: 디렉토리 골격 + 빈 룰 파일

**Files:**
- Create: `infra/prometheus/rules/carry-baseline.rules.yml`
- Create: `infra/prometheus/rules/tests/carry-baseline.test.yml`
- Create: `infra/alertmanager/alertmanager.yml`

- [x] **Step 1: 빈 룰 그룹 작성**

`infra/prometheus/rules/carry-baseline.rules.yml`:
```yaml
# Phase 3.4 — 알럿 기준선
# 룰별 PromQL/threshold/route 근거는 docs/superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md 섹션 4.
groups:
  - name: carry-baseline
    interval: 30s
    rules: []
```

- [x] **Step 2: 빈 테스트 파일 작성**

`infra/prometheus/rules/tests/carry-baseline.test.yml`:
```yaml
# promtool test rules — Phase 3.4 알럿 기준선
rule_files:
  - ../carry-baseline.rules.yml
evaluation_interval: 30s
tests: []
```

- [x] **Step 3: Alertmanager 골격 작성**

`infra/alertmanager/alertmanager.yml`:
```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'route']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'webhook-logger'
  routes:
    - matchers:
        - route="oncall"
      receiver: 'slack-critical'
      continue: true
    - matchers:
        - route="slack"
      receiver: 'slack-warning'
      continue: true
    - matchers:
        - route="business"
      receiver: 'webhook-logger'

receivers:
  - name: 'webhook-logger'
    webhook_configs:
      - url: 'http://alert-webhook-logger:8080/'
        send_resolved: true
  - name: 'slack-critical'
    # 본 PR에서는 placeholder URL을 webhook-logger의 stub 경로로 직접 가리킨다.
    # env 주입(SLACK_WEBHOOK_URL_*) 자동화는 후속 PR로 미룸 — Alertmanager는 env 치환을
    # 자체적으로 안 하고 envsubst init이 필요한데, 본 PoC에선 학습 목적상 불필요.
    slack_configs:
      - api_url: 'http://alert-webhook-logger:8080/slack-stub-critical'
        channel: '#alerts-critical'
        send_resolved: true
        title: '[CRITICAL] {{ .CommonLabels.alertname }}'
        text: |
          {{ range .Alerts }}
          *Summary:* {{ .Annotations.summary }}
          *Description:* {{ .Annotations.description }}
          *Runbook:* {{ .Annotations.runbook_url }}
          {{ end }}
  - name: 'slack-warning'
    slack_configs:
      - api_url: 'http://alert-webhook-logger:8080/slack-stub-warning'
        channel: '#alerts-warning'
        send_resolved: true
        title: '[WARNING] {{ .CommonLabels.alertname }}'
        text: |
          {{ range .Alerts }}
          *Summary:* {{ .Annotations.summary }}
          *Description:* {{ .Annotations.description }}
          *Runbook:* {{ .Annotations.runbook_url }}
          {{ end }}
```

- [x] **Step 4: amtool check-config 통과 확인**

PowerShell:
```powershell
docker run --rm -v ${PWD}/infra/alertmanager:/a prom/alertmanager:v0.27.0 amtool check-config /a/alertmanager.yml
```
Expected: `Checking '/a/alertmanager.yml'  SUCCESS`

- [x] **Step 5: promtool check rules 통과 확인 (빈 룰)**

PowerShell:
```powershell
docker run --rm -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 promtool check rules /p/rules/carry-baseline.rules.yml
```
Expected: `SUCCESS: 0 rules found`

- [x] **Step 6: Commit**

```powershell
git add infra/prometheus/rules/ infra/alertmanager/
git commit -F (Write the commit message via temp file per powershell-git-commit-message-via-file memory)
```

커밋 메시지(별 파일에 작성 후 `git commit -F`):
```
chore(observability): Phase 3.4 — 알럿 인프라 골격 (빈 룰 + AM 라우팅)

amtool/promtool 검증이 통과하는 최소 골격. 후속 태스크에서 8개 룰을
TDD red→green으로 채운다.
```

---

### Task 1.2: Rule #1 `ApiHighLatencyP99` (red → green)

**Files:**
- Modify: `infra/prometheus/rules/tests/carry-baseline.test.yml`
- Modify: `infra/prometheus/rules/carry-baseline.rules.yml`

- [x] **Step 1: 테스트 2 케이스 추가 (음성·양성)**

`tests:` 블록에 추가:
```yaml
  - interval: 30s
    name: ApiHighLatencyP99 음성 (p99 ~2s)
    input_series:
      # 카운터 형태: le="2"와 le="+Inf"가 동일 속도로 증가 → p99 ~2s
      - series: http_server_requests_seconds_bucket{le="2",uri="/orders"}
        values: '0+99x20'
      - series: http_server_requests_seconds_bucket{le="+Inf",uri="/orders"}
        values: '0+100x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: ApiHighLatencyP99
        # 음성 — exp_alerts 생략 = no fire
  - interval: 30s
    name: ApiHighLatencyP99 firing (p99 ~4s)
    input_series:
      # 카운터 형태: le="2" 50% 비율, le="4" 99% 비율, le="+Inf" 100% 비율로 증가
      # → quantile 0.99는 le="4" 버킷 근처 → p99 ~4s
      - series: http_server_requests_seconds_bucket{le="2",uri="/orders"}
        values: '0+50x20'
      - series: http_server_requests_seconds_bucket{le="4",uri="/orders"}
        values: '0+99x20'
      - series: http_server_requests_seconds_bucket{le="+Inf",uri="/orders"}
        values: '0+100x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: ApiHighLatencyP99
        exp_alerts:
          - exp_labels:
              severity: warning
              route: slack
              team: carry
              service: carry-platform
```

- [x] **Step 2: promtool test rules 실행 — FAIL 확인**

PowerShell:
```powershell
docker run --rm -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 promtool test rules /p/rules/tests/carry-baseline.test.yml
```
Expected: `FAILED` (alertname ApiHighLatencyP99 not defined)

- [x] **Step 3: 룰 추가**

`carry-baseline.rules.yml`의 `rules:` 블록에 추가:
```yaml
      - alert: ApiHighLatencyP99
        expr: histogram_quantile(0.99, sum by(le) (rate(http_server_requests_seconds_bucket[5m]))) > 3
        for: 5m
        labels:
          severity: warning
          route: slack
          team: carry
          service: carry-platform
        annotations:
          summary: 'API p99 응답 시간이 3초를 초과했습니다'
          description: 'p99={{ $value }}s (>3s, 5분 평균)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/api-p99-latency.md'
```

- [x] **Step 4: promtool test rules 실행 — PASS 확인**

Expected: `SUCCESS`

- [x] **Step 5: Commit**

```
feat(observability): Phase 3.4 — Rule #1 ApiHighLatencyP99
```

---

### Task 1.3: Rule #2 `ApiHighErrorRate`

**Files:** 동일

- [x] **Step 1: 테스트 추가**

```yaml
  - interval: 30s
    name: ApiHighErrorRate 음성
    input_series:
      - series: http_server_requests_seconds_count{status="200"}
        values: '0+10x20'
      - series: http_server_requests_seconds_count{status="500"}
        values: '0+0x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: ApiHighErrorRate
  - interval: 30s
    name: ApiHighErrorRate firing
    input_series:
      # 5xx 10% (200 OK 90개, 500 10개 / 분)
      - series: http_server_requests_seconds_count{status="200"}
        values: '0+90x20'
      - series: http_server_requests_seconds_count{status="500"}
        values: '0+10x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: ApiHighErrorRate
        exp_alerts:
          - exp_labels:
              severity: warning
              route: slack
              team: carry
              service: carry-platform
```

- [x] **Step 2: promtool test — FAIL 확인**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: ApiHighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 0.001) > 0.05
        for: 5m
        labels:
          severity: warning
          route: slack
          team: carry
          service: carry-platform
        annotations:
          summary: '5xx 에러율이 5%를 초과했습니다'
          description: '5xx rate={{ $value | humanizePercentage }} (>5%, 5분 평균)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/api-error-rate.md'
```

- [x] **Step 4: promtool test — PASS 확인**
- [x] **Step 5: Commit**

```
feat(observability): Phase 3.4 — Rule #2 ApiHighErrorRate
```

---

### Task 1.4: Rule #3 `KafkaConsumerLag`

- [x] **Step 1: 테스트 추가 (lag 토픽 라벨 보존 확인 — by(topic))**

```yaml
  - interval: 30s
    name: KafkaConsumerLag 음성
    input_series:
      - series: kafka_consumer_fetch_manager_records_lag{topic="order-created"}
        values: '500x25'
    alert_rule_test:
      - eval_time: 11m
        alertname: KafkaConsumerLag
  - interval: 30s
    name: KafkaConsumerLag firing
    input_series:
      - series: kafka_consumer_fetch_manager_records_lag{topic="order-created"}
        values: '2000x25'
    alert_rule_test:
      - eval_time: 11m
        alertname: KafkaConsumerLag
        exp_alerts:
          - exp_labels:
              severity: warning
              route: slack
              team: carry
              service: carry-platform
              topic: 'order-created'
```

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: KafkaConsumerLag
        expr: sum by(topic) (kafka_consumer_fetch_manager_records_lag) > 1000
        for: 10m
        labels:
          severity: warning
          route: slack
          team: carry
          service: carry-platform
        annotations:
          summary: 'Kafka consumer lag이 1000을 초과했습니다 (topic={{ $labels.topic }})'
          description: 'lag={{ $value }} (>1000, 10분 지속)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/kafka-consumer-lag.md'
```

- [x] **Step 4: promtool test — PASS**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #3 KafkaConsumerLag`

---

### Task 1.5: Rule #4 `DispatchTimeoutRate` (메트릭 부재 — absent 가드 우선 검증)

⚠ 본 룰의 핵심은 `carry_dispatch_timeout_total` 메트릭이 **아직 코드에 없음**. 음성 케이스(absent → no fire)가 회귀 방어선이므로 먼저 작성.

- [x] **Step 1: 테스트 추가**

```yaml
  - interval: 30s
    name: DispatchTimeoutRate 음성 (timeout 메트릭 absent)
    input_series:
      - series: carry_dispatch_accepted_total{via="claim"}
        values: '0+10x40'
      - series: carry_dispatch_rejected_total
        values: '0+1x40'
      # carry_dispatch_timeout_total 일부러 정의 안 함
    alert_rule_test:
      - eval_time: 16m
        alertname: DispatchTimeoutRate
  - interval: 30s
    name: DispatchTimeoutRate firing
    input_series:
      - series: carry_dispatch_accepted_total{via="claim"}
        values: '0+7x40'
      - series: carry_dispatch_rejected_total
        values: '0+0x40'
      - series: carry_dispatch_timeout_total
        values: '0+3x40'
    alert_rule_test:
      - eval_time: 16m
        alertname: DispatchTimeoutRate
        exp_alerts:
          - exp_labels:
              severity: warning
              route: business
              team: carry
              service: carry-platform
```

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가 (absent 가드 포함)**

```yaml
      - alert: DispatchTimeoutRate
        # ⚠ carry_dispatch_timeout_total 메트릭은 아직 미구현(known debt #1).
        # `and sum(rate(...timeout...) > 0` 가드로 메트릭 absent 시 firing 차단.
        # 메트릭 도입 시 룰이 자동 활성화된다.
        expr: |
          (
            sum(rate(carry_dispatch_timeout_total[15m]))
            / clamp_min(
                sum(rate(carry_dispatch_accepted_total[15m]))
                + sum(rate(carry_dispatch_rejected_total[15m]))
                + sum(rate(carry_dispatch_timeout_total[15m])),
                0.001)
          ) > 0.2
          and sum(rate(carry_dispatch_timeout_total[15m])) > 0
        for: 15m
        labels:
          severity: warning
          route: business
          team: carry
          service: carry-platform
        annotations:
          summary: '배차 타임아웃율이 20%를 초과했습니다'
          description: 'timeout rate={{ $value | humanizePercentage }} (>20%, 15분 평균)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/dispatch-timeout-rate.md'
```

- [x] **Step 4: promtool test — PASS (음성·양성 둘 다)**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #4 DispatchTimeoutRate (absent 가드)`

---

### Task 1.6: Rule #5 `PaymentHighFailureRate` (critical)

- [x] **Step 1: 테스트 추가**

```yaml
  - interval: 30s
    name: PaymentHighFailureRate 음성
    input_series:
      - series: carry_payment_success_total{pg="toss"}
        values: '0+10x20'
      - series: carry_payment_failure_total{pg="toss"}
        values: '0+0x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: PaymentHighFailureRate
  - interval: 30s
    name: PaymentHighFailureRate firing
    input_series:
      - series: carry_payment_success_total{pg="toss"}
        values: '0+9x20'
      - series: carry_payment_failure_total{pg="toss"}
        values: '0+1x20'
    alert_rule_test:
      - eval_time: 6m
        alertname: PaymentHighFailureRate
        exp_alerts:
          - exp_labels:
              severity: critical
              route: oncall
              team: carry
              service: carry-platform
```

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: PaymentHighFailureRate
        expr: |
          sum(rate(carry_payment_failure_total[5m]))
          / clamp_min(
              sum(rate(carry_payment_success_total[5m]))
              + sum(rate(carry_payment_failure_total[5m])),
              0.001) > 0.05
        for: 5m
        labels:
          severity: critical
          route: oncall
          team: carry
          service: carry-platform
        annotations:
          summary: '결제 실패율이 5%를 초과했습니다 (CRITICAL)'
          description: 'failure rate={{ $value | humanizePercentage }} (>5%, 5분 평균)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/payment-failure-rate.md'
```

- [x] **Step 4: promtool test — PASS**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #5 PaymentHighFailureRate (critical)`

---

### Task 1.7: Rule #6 `KafkaDlqNonEmpty`

- [x] **Step 1: 테스트 추가**

```yaml
  - interval: 30s
    name: KafkaDlqNonEmpty 음성 (DLQ 변화 없음)
    input_series:
      # 30s × 14 = 7분 동안 카운터 5로 평탄 → increase=0 → no fire
      - series: carry_kafka_dlq_total{topic="order-created.DLQ",exception="RuntimeException"}
        values: '5+0x13'
    alert_rule_test:
      - eval_time: 6m
        alertname: KafkaDlqNonEmpty
  - interval: 30s
    name: KafkaDlqNonEmpty firing (5분 내 카운터 증가)
    input_series:
      # 30s 간격으로 0..5에서 1씩 늘다가 5에서 평탄. eval_time=6m 직전 5분 동안 increase>0.
      - series: carry_kafka_dlq_total{topic="order-created.DLQ",exception="RuntimeException"}
        values: '0 1 2 3 4 5 5 5 5 5 5 5 5 5'
    alert_rule_test:
      # for: 1m + 5분 증분 lookback이라 eval_time을 충분히 뒤로 둔다 (6분).
      # 시리즈도 그 시점 이전 5분 동안 증분이 살아있도록 설계.
      - eval_time: 6m
        alertname: KafkaDlqNonEmpty
        exp_alerts:
          - exp_labels:
              severity: warning
              route: slack
              team: carry
              service: carry-platform
              topic: 'order-created.DLQ'
```

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: KafkaDlqNonEmpty
        expr: sum by(topic) (increase(carry_kafka_dlq_total[5m])) > 0
        for: 1m
        labels:
          severity: warning
          route: slack
          team: carry
          service: carry-platform
        annotations:
          summary: 'Kafka DLQ에 메시지가 쌓였습니다 (topic={{ $labels.topic }})'
          description: '5분 증분={{ $value }}건'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/kafka-dlq-nonempty.md'
```

- [x] **Step 4: promtool test — PASS**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #6 KafkaDlqNonEmpty`

---

### Task 1.8: Rule #7 `HikariPoolSaturation`

- [x] **Step 1: 테스트 추가**

```yaml
  - interval: 30s
    name: HikariPoolSaturation 음성
    input_series:
      - series: hikaricp_connections_active{pool="CarryHikariPool"}
        values: '10x25'
      - series: hikaricp_connections_max{pool="CarryHikariPool"}
        values: '20x25'
    alert_rule_test:
      - eval_time: 11m
        alertname: HikariPoolSaturation
  - interval: 30s
    name: HikariPoolSaturation firing
    input_series:
      - series: hikaricp_connections_active{pool="CarryHikariPool"}
        values: '18x25'
      - series: hikaricp_connections_max{pool="CarryHikariPool"}
        values: '20x25'
    alert_rule_test:
      - eval_time: 11m
        alertname: HikariPoolSaturation
        exp_alerts:
          - exp_labels:
              severity: warning
              route: slack
              team: carry
              service: carry-platform
              pool: 'CarryHikariPool'
```

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: HikariPoolSaturation
        expr: |
          hikaricp_connections_active{pool="CarryHikariPool"}
          / hikaricp_connections_max{pool="CarryHikariPool"} > 0.8
        for: 10m
        labels:
          severity: warning
          route: slack
          team: carry
          service: carry-platform
        annotations:
          summary: 'HikariCP 커넥션 풀 사용률이 80%를 초과했습니다'
          description: 'active/max={{ $value | humanizePercentage }} (>80%, 10분 지속)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/hikari-pool-saturation.md'
```

- [x] **Step 4: promtool test — PASS**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #7 HikariPoolSaturation`

---

### Task 1.9: Rule #8 `OrderVolumeDropDoD` (24h 가드 핵심)

⚠ `offset 1d`라 promtool 테스트에 29시간+ 시리즈 필요. 또 `for: 30m`이 적용되므로 firing 조건이 충분히 길게 유지되어야 한다. `interval: 30m` + 60+ 샘플로 설계.

**시리즈 산술 의도 (양성 케이스, eval_time = 28h)**

| 시점 | 윈도우 | 카운터 행동 | 의도 |
|---|---|---|---|
| t=0 ~ 3h (yesterday pre-traffic) | — | 카운터 0 평탄 | 어제 윈도우 직전 |
| t=3h ~ 4h (yesterday window) | `rate([1h] offset 1d)` at t=28h | 0 → 14400 | 어제 rate = 4/s (guard > 0.01 통과) |
| t=4h ~ 27h | — | 14400/h로 지속 증가 | 윈도우 외부 |
| t=27h ~ 28h (today window) | `rate([1h])` at t=28h | 14400*24=345600 → 349200 | 오늘 rate = 1/s |
| t=28h~ (for: 30m 유지) | — | 3600/h로 지속 | firing 유지 |

오늘/어제 = 0.25 < 0.5 → fires. 어제 rate = 4/s > 0.01/s → guard 통과.

**음성 케이스 (eval_time = 28h)**: 카운터를 평탄(`100+0x59`)으로 두면 모든 rate=0 → guard 실패 → no fire.

- [x] **Step 1: 테스트 추가 (음성 + 양성)**

```yaml
  - interval: 30m
    name: OrderVolumeDropDoD 음성 (어제 rate 0 → guard 작동)
    input_series:
      - series: carry_order_created_total
        # 60 samples (30m × 60 = 30h), 카운터 평탄
        values: '100+0x59'
    alert_rule_test:
      - eval_time: 28h
        alertname: OrderVolumeDropDoD
        # exp_alerts 생략 = no fire
  - interval: 30m
    name: OrderVolumeDropDoD firing (DoD drop 75%)
    input_series:
      - series: carry_order_created_total
        # 30m × 62 = 31h. 시점별 의도 (position k → t = 30m × k):
        #   positions 0~6   (t=0~3h):    카운터 0          — 어제 윈도우 직전 (seg1: 0+0x6 → 7 samples)
        #   positions 7~55  (t=3.5~27.5h): 0→7200*48=345600 — 7200 증분/step = 14400/h (4/s); 어제 윈도우 (3h, 4h] 포함
        #   positions 56~61 (t=28~30.5h):  349200→358200    — 1800 증분/step = 3600/h (1/s) — 오늘 drop (seg3: 349200+1800x5 → 6 samples)
        # 어제 윈도우 (3h, 4h]: rate (14400-0)/3600 = 4/s
        # 오늘 윈도우 (27h, 28h]: rate (349200-345600)/3600 = 1/s
        # ratio = 0.25 < 0.5 → fires (for: 30m도 28h~29h 구간에서 유지)
        values: '0+0x6 0+7200x48 349200+1800x5'
    alert_rule_test:
      - eval_time: 28h
        alertname: OrderVolumeDropDoD
        exp_alerts:
          - exp_labels:
              severity: warning
              route: business
              team: carry
              service: carry-platform
```

⚠ **실행 시 시리즈 미세조정 필요할 수 있음.** PromQL `rate()`의 정확한 계산은 promtool 내부 step 평가에 의존한다. 위 값으로 promtool이 expected와 다른 ratio를 내면 다음 순서로 디버그:
1. 시리즈 길이를 늘려본다 (`x59` → `x71` 등 30h+ 데이터 확보)
2. `rate([1h] offset 1d)` 결과를 직접 확인 — promtool eval 중간값 출력 옵션 없음, 시간 가산기 노트북 등으로 수동 계산
3. firing 조건이 한 evaluation cycle만 충족된다면 eval_time을 `28h30m` 또는 `29h`로 늘려 for: 30m 통과시킨다
4. 그래도 실패하면 `for: 30m`을 임시로 `for: 0m`으로 낮춰 expr 자체가 양성·음성을 분리하는지 먼저 검증 후 원복

- [x] **Step 2: promtool test — FAIL**
- [x] **Step 3: 룰 추가**

```yaml
      - alert: OrderVolumeDropDoD
        expr: |
          (
            sum(rate(carry_order_created_total[1h]))
            / clamp_min(sum(rate(carry_order_created_total[1h] offset 1d)), 0.001)
          ) < 0.5
          and sum(rate(carry_order_created_total[1h] offset 1d)) > 0.01
        for: 30m
        labels:
          severity: warning
          route: business
          team: carry
          service: carry-platform
        annotations:
          summary: '주문 생성률이 전일 대비 50% 이하로 떨어졌습니다'
          description: 'today/yesterday={{ $value | humanizePercentage }} (<50%, 1h 비교)'
          runbook_url: 'https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/order-volume-drop.md'
```

- [x] **Step 4: promtool test — PASS (값 조정 필요 시 input_series 미세조정)**
- [x] **Step 5: Commit**: `feat(observability): Phase 3.4 — Rule #8 OrderVolumeDropDoD (DoD 가드)`

---

### Task 1.10: Chunk 1 전체 검증

- [x] **Step 1: 전체 룰/테스트 일괄 검증**

```powershell
docker run --rm -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 promtool check rules /p/rules/carry-baseline.rules.yml
docker run --rm -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 promtool test rules /p/rules/tests/carry-baseline.test.yml
docker run --rm -v ${PWD}/infra/alertmanager:/a prom/alertmanager:v0.27.0 amtool check-config /a/alertmanager.yml
```

Expected:
- `SUCCESS: 8 rules found`
- `Unit Testing: ... SUCCESS`
- `Checking '/a/alertmanager.yml'  SUCCESS`

- [x] **Step 2: 룰 카탈로그 무결성 확인 — 모든 알럿이 runbook_url을 가지고 있는지**

```powershell
docker run --rm -v ${PWD}/infra/prometheus:/p prom/prometheus:v3.0.1 promtool check rules /p/rules/carry-baseline.rules.yml
Select-String -Path infra/prometheus/rules/carry-baseline.rules.yml -Pattern 'runbook_url' | Measure-Object | Select-Object -ExpandProperty Count
```
Expected: 8

- [x] **Step 3: 청크 종료 commit (있으면)**

Chunk 1 내부 commit이 충분히 잦아 추가 commit 불필요. 다음 청크로.

---

## Chunk 2: 인프라 통합 (docker-compose + prometheus.yml + Grafana)

### Task 2.1: prometheus.yml 패치

**Files:**
- Modify: `infra/prometheus.yml`

- [x] **Step 1: rule_files + alerting 섹션 추가**

기존 파일 끝에 추가:
```yaml
rule_files:
  - "/etc/prometheus/rules/*.rules.yml"   # *.rules.yml만 — 테스트 픽스처(*.test.yml) 제외

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

⚠ 글롭은 반드시 `*.rules.yml`. `*.yml`로 두면 `tests/carry-baseline.test.yml`까지 파싱하다 부팅 실패.

- [x] **Step 2: promtool check config 통과 확인 (전체 prometheus.yml)**

```powershell
docker run --rm -v ${PWD}/infra:/i prom/prometheus:v3.0.1 promtool check config /i/prometheus.yml
```
Expected: `SUCCESS` (rules 파일 경로는 컨테이너 마운트가 없어 경고 가능 — `--lint-fatal` 없이 SUCCESS면 통과)

- [ ] **Step 3: Commit**

```
feat(observability): Phase 3.4 — Prometheus rule_files + Alertmanager 타깃
```

### Task 2.2: docker-compose alertmanager + alert-webhook-logger 서비스 추가

**Files:**
- Modify: `docker-compose.yml`

- [x] **Step 1: alertmanager 서비스 + alert-webhook-logger 서비스 + 신규 볼륨 추가**

`grafana:` 서비스 다음, `volumes:` 블록 이전에 삽입:
```yaml
  # Phase 3.4 — 알럿 라우팅
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: carry-alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./infra/alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - carry-alertmanager-data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
    # 실 Slack/PagerDuty 연결은 후속 PR. 본 PR에서 alertmanager.yml의 api_url을
    # alert-webhook-logger의 stub 경로로 하드코딩 (env 치환은 Alertmanager 자체에서
    # 안 되므로 환경변수 주입 불필요).
    depends_on:
      - alert-webhook-logger

  # Phase 3.4 — Alertmanager가 보낸 webhook 페이로드를 stdout으로 echo.
  # `docker logs -f carry-alert-webhook-logger`로 firing 시 페이로드 확인.
  # 이미지 선택: mendhak/http-https-echo는 별도 hooks.json 설정 없이 모든 경로의
  # POST body를 stdout으로 echo하므로 alertmanager의 webhook 수신 검증에 직접 사용 가능.
  # 대안 adnanh/webhook은 hooks.json 강제로 추가 설정 비용이 있어 본 PoC에서 부적합.
  alert-webhook-logger:
    image: mendhak/http-https-echo:34
    container_name: carry-alert-webhook-logger
    ports:
      - "9095:8080"
    environment:
      HTTP_PORT: "8080"
      LOG_WITHOUT_NEWLINE: "true"
```

`volumes:` 블록에 추가:
```yaml
  carry-alertmanager-data:
```

prometheus 서비스 `volumes:`에 룰 디렉토리 마운트 추가:
```yaml
  prometheus:
    # ... 기존 설정 ...
    volumes:
      - ./infra/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./infra/prometheus/rules:/etc/prometheus/rules:ro    # 신규 (Phase 3.4)
      - carry-prometheus-data:/prometheus
```

- [x] **Step 2: docker-compose config 검증 (문법)**

```powershell
docker compose config --quiet
```
Expected: 출력 없음 (성공)

- [ ] **Step 3: Commit**

```
feat(observability): Phase 3.4 — docker-compose에 Alertmanager + webhook-logger 추가
```

### Task 2.3: Grafana 대시보드에 "Active firing alerts" 패널 추가

**Files:**
- Modify: `infra/grafana/dashboards/carry-business.json`

JSON 직접 문자열 편집은 부서지기 쉬우므로 **Edit 도구의 정확한 anchor + 신규 패널 삽입** 방식을 쓴다. 기존 마지막 패널(id 14 "배달 소요 시간 p50 / p95")의 닫는 `}` + 다음 라인의 `]` 사이에 신규 패널을 추가한다.

- [x] **Step 1: 기존 마지막 패널의 정확한 anchor 확인**

```powershell
Get-Content infra/grafana/dashboards/carry-business.json | Select-String -Pattern '"id": 14' -Context 0,30
```
Expected: id 14 패널의 시작부터 닫는 `}` 까지의 30 라인 출력. 이 출력에서 마지막 줄(닫는 `}`)과 그 다음 `]`을 anchor로 식별.

- [x] **Step 2: Edit 도구로 마지막 패널 닫는 `}` 직후 신규 패널 삽입**

기존 (id 14 패널의 닫는 부분):
```json
    }
  ],
```

신규로 교체:
```json
    },
    {
      "type": "stat",
      "title": "Active firing alerts",
      "gridPos": { "h": 6, "w": 12, "x": 12, "y": 53 },
      "id": 15,
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "targets": [
        { "expr": "count(ALERTS{alertstate=\"firing\"})", "refId": "A" }
      ],
      "options": {
        "reduceOptions": { "calcs": ["lastNotNull"] },
        "colorMode": "background",
        "graphMode": "none",
        "textMode": "value"
      },
      "fieldConfig": {
        "defaults": {
          "unit": "short",
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 1 },
              { "color": "red", "value": 5 }
            ]
          }
        },
        "overrides": []
      }
    }
  ],
```

⚠ Edit 호출 시 정확한 컨텍스트(들여쓰기·트레일링 콤마 포함) 확인 후 진행. JSON 마지막 객체 뒤에는 콤마 없음 규칙.

- [x] **Step 2: JSON 유효성 검증**

```powershell
Get-Content infra/grafana/dashboards/carry-business.json | ConvertFrom-Json | Out-Null
if ($?) { Write-Host "Valid JSON" }
```
Expected: `Valid JSON`

- [x] **Step 3: 신규 패널의 id가 unique한지 확인**

```powershell
$json = Get-Content infra/grafana/dashboards/carry-business.json -Raw | ConvertFrom-Json
$ids = $json.panels | ForEach-Object { $_.id }
$dups = $ids | Group-Object | Where-Object { $_.Count -gt 1 }
if ($null -eq $dups) { "No duplicate ids" } else { "DUPLICATE: $($dups | Format-Table)" }
```
Expected: `No duplicate ids`

- [ ] **Step 4: Commit**

```
feat(observability): Phase 3.4 — Grafana에 Active firing alerts 패널 추가
```

### Task 2.4: Chunk 2 통합 스모크 (수동)

- [x] **Step 1: 전체 스택 부팅**

```powershell
docker compose up -d prometheus alertmanager alert-webhook-logger grafana
Start-Sleep -Seconds 10
docker compose ps
```
Expected: `prometheus`, `alertmanager`, `alert-webhook-logger`, `grafana` 4개 모두 `Up`/healthy

- [x] **Step 2: Prometheus 룰 로드 확인**

브라우저 `http://localhost:9090/rules` → 그룹 `carry-baseline`에 8개 룰 표시 확인 (스크린샷 PR description용으로 보관)
또는 API:
```powershell
(Invoke-RestMethod http://localhost:9090/api/v1/rules).data.groups[0].rules.Count
```
Expected: `8`

- [x] **Step 3: Alertmanager 라우팅 트리 확인**

브라우저 `http://localhost:9093/#/status` → Cluster: ready, Config: 표시되는 alertmanager.yml과 일치. `http://localhost:9093/#/alerts` → 현재 firing 없음.

- [x] **Step 4: 임의 알럿 firing 트리거 (수동 검증)**

```powershell
$alert = @'
[{
  "labels": {
    "alertname": "TestFireFromPlan",
    "severity": "warning",
    "route": "slack",
    "team": "carry",
    "service": "carry-platform"
  },
  "annotations": {
    "summary": "수동 스모크 테스트",
    "description": "Plan Chunk 2 검증",
    "runbook_url": "https://github.com/Coinlaundryapp/carry-platform/blob/develop/docs/operations/runbooks/README.md"
  }
}]
'@
$bytes = [System.Text.Encoding]::UTF8.GetBytes($alert)
Invoke-RestMethod -Method Post -Uri http://localhost:9093/api/v2/alerts `
  -ContentType 'application/json; charset=utf-8' -Body $bytes
Start-Sleep -Seconds 5
docker logs --tail 50 carry-alert-webhook-logger
```
Expected: webhook-logger 로그에 POST body 출력(label/annotation 포함).

- [x] **Step 5: 정리**

```powershell
docker compose down -v   # PR 실험 데이터 정리 (선택)
```

이 단계의 결과(스크린샷·로그 발췌)는 PR description "수동 스모크" 섹션에 첨부.

- [x] **Step 6: 청크 종료 (commit 추가 없음 — 검증 단계)**

---

## Chunk 3: CI 워크플로 + 런북 + 마무리

### Task 3.1: GitHub Actions validate-alerts 워크플로

**Files:**
- Create: `.github/workflows/validate-alerts.yml`

- [ ] **Step 1: 워크플로 작성**

```yaml
name: validate-alerts

on:
  push:
    paths:
      - 'infra/prometheus/**'
      - 'infra/alertmanager/**'
      - '.github/workflows/validate-alerts.yml'
  pull_request:
    paths:
      - 'infra/prometheus/**'
      - 'infra/alertmanager/**'
      - '.github/workflows/validate-alerts.yml'

jobs:
  promtool-check-rules:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: promtool check rules
        # --entrypoint promtool: prom/prometheus 이미지의 default ENTRYPOINT는
        # prometheus 바이너리이므로 promtool을 직접 호출하려면 명시 오버라이드 필요.
        run: |
          docker run --rm --entrypoint promtool \
            -v "$PWD/infra/prometheus":/p \
            prom/prometheus:v3.0.1 \
            check rules /p/rules/carry-baseline.rules.yml

  promtool-test-rules:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: promtool test rules
        run: |
          docker run --rm --entrypoint promtool \
            -v "$PWD/infra/prometheus":/p \
            prom/prometheus:v3.0.1 \
            test rules /p/rules/tests/carry-baseline.test.yml

  amtool-check-config:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: amtool check-config
        # --entrypoint amtool: prom/alertmanager 이미지의 default ENTRYPOINT는
        # alertmanager 바이너리. amtool 직접 호출 시 오버라이드 필요.
        run: |
          docker run --rm --entrypoint amtool \
            -v "$PWD/infra/alertmanager":/a \
            prom/alertmanager:v0.27.0 \
            check-config /a/alertmanager.yml

  runbook-link-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 룰의 runbook_url이 가리키는 파일이 모두 존재하는지
        run: |
          set -e
          missing=0
          for url in $(grep -oE 'runbook_url: .*' infra/prometheus/rules/carry-baseline.rules.yml | awk '{print $2}' | tr -d "'"); do
            # GitHub URL → repo 상대 경로 변환
            path=$(echo "$url" | sed -E 's|.*/develop/||')
            if [ ! -f "$path" ]; then
              echo "MISSING: $path (from $url)"
              missing=$((missing+1))
            fi
          done
          if [ $missing -gt 0 ]; then
            echo "Missing $missing runbook(s)"
            exit 1
          fi
          echo "All runbook_url paths resolve."
```

- [ ] **Step 2: 로컬에서 act 또는 syntax 검사 (선택)**

```powershell
# 빠른 YAML 문법 확인
Get-Content .github/workflows/validate-alerts.yml | python -c "import sys,yaml; yaml.safe_load(sys.stdin)"
```
Expected: 출력 없음 (성공). Python 없으면 PR 푸시 후 GitHub UI에서 워크플로 파일 파싱 확인.

- [ ] **Step 3: Commit**

```
ci: Phase 3.4 — validate-alerts 워크플로 (promtool/amtool + 런북 링크)
```

### Task 3.2: 런북 8개 + README

**Files:**
- Create: `docs/operations/runbooks/README.md`
- Create: `docs/operations/runbooks/api-p99-latency.md`
- Create: `docs/operations/runbooks/api-error-rate.md`
- Create: `docs/operations/runbooks/kafka-consumer-lag.md`
- Create: `docs/operations/runbooks/dispatch-timeout-rate.md`
- Create: `docs/operations/runbooks/payment-failure-rate.md`
- Create: `docs/operations/runbooks/kafka-dlq-nonempty.md`
- Create: `docs/operations/runbooks/hikari-pool-saturation.md`
- Create: `docs/operations/runbooks/order-volume-drop.md`

- [ ] **Step 1: README + 매핑 테이블 + 수동 스모크 체크리스트 작성**

`docs/operations/runbooks/README.md` 구조:
```markdown
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
```

- [ ] **Step 2: 런북 공통 템플릿 + 각 런북별 차별점 작성**

각 런북은 80~150줄 분량. 다음 골격을 따르되, **"흔한 원인" 표는 최소 3행**, **"즉시 확인할 것" 단계는 4~6단계**, **PromQL 예시는 실제로 동작하는 표현식**으로 채운다(placeholder 금지).

공통 골격:
```markdown
# <Alert Name>

## 개요
- **Alert ID:** `<AlertName>`
- **Severity:** warning|critical
- **Route:** slack|oncall|business
- **트리거 조건:** <expr 한 줄 한국어 요약>
- **for:** <X분>
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가
<현상 1단락 — 사용자/시스템 영향 위주>

## 즉시 확인할 것 (5분 이내)
1. **Grafana 대시보드** `Carry — Business & Resilience` (http://localhost:3000/d/carry-business)에서 <관련 패널> 확인
2. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 알럿 확인
3. **Saga 상관관계**: Phase 3.3에서 도입한 `correlationId` MDC로 관련 로그 추적
4. <알럿별 차별 단계>

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| <원인 1> | <메트릭/로그 신호> | <조치> |
| <원인 2> | <...> | <...> |
| <원인 3> | <...> | <...> |

## 에스컬레이션 기준
- <X분 이상 firing 또는 Y 조건 충족 시>
- <추가 critical 알럿 동시 발생 시>

## 관련 메트릭/대시보드 패널
- Grafana 패널: <이름> (id: <X>)
- 상세 PromQL:
  ```promql
  <자주 쓸 진단용 쿼리>
  ```

## 관련 ROADMAP/known-debts
- <링크>
```

각 런북의 차별점 — **이 표의 내용을 골격에 직접 반영**:

| 런북 | "즉시 확인" 추가 단계 | "흔한 원인" 3행 (원인/신호/대응) | 에스컬레이션 | 관련 PromQL |
|---|---|---|---|---|
| `api-p99-latency.md` | 직전 배포 commit/tag 확인; `resilience4j_circuitbreaker_state` 확인 | (1) DB 슬로우 쿼리 / Hikari leak detection 경고 / 쿼리 튜닝·인덱스 (2) 외부 API 지연 / CB state=open / Phase 2.2 CB 자연 fallback 대기 (3) GC pause / `jvm_gc_pause_seconds` p99 spike / ZGC GC log 확인 | 30분 이상 또는 p99 > 5s | `histogram_quantile(0.99, sum by(le, uri) (rate(http_server_requests_seconds_bucket[5m])))` (URI 별 분해) |
| `api-error-rate.md` | 최근 PR 머지 이력 확인; `kafka-connect` 상태 확인 | (1) 배포 회귀 / 직전 commit 시점부터 5xx 폭증 / rollback (2) DB connection 고갈 / `HikariPoolSaturation` 동시 firing / pool size env 증가 (3) 외부 인증 만료 / 401·403 + 5xx 혼재 / token 갱신 | 5xx > 10% 또는 `/api/v1/payments/**` 5xx 발생 | `sum by(uri, status) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))` |
| `kafka-consumer-lag.md` | `kafka-consumer-groups.sh --describe --bootstrap-server localhost:9092 --group <group>` | (1) DB 쓰기 지연 / `HikariPoolSaturation` 동시 / pool tuning 또는 쿼리 (2) 외부 API 실패 / `resilience4j_circuitbreaker_state` open / CB 자연 fallback 대기 (3) 컨슈머 OOM 재시작 / `kafka_consumer_records_consumed_total` 평탄 + 컨테이너 restart 카운트 / heap 증가 또는 max-poll-records 축소 | 30분 이상 지속 또는 lag > 10000 | `sum by(topic, consumergroup) (kafka_consumer_fetch_manager_records_lag)` |
| `dispatch-timeout-rate.md` ⚠ | (🚧 본 룰은 메트릭 부재로 비활성 상태) | (활성화 후) (1) 기사 풀 고갈 / 특정 지역 활성 기사 0 / 캠페인·인센티브 (2) 매칭 알고리즘 회귀 / 직전 배포 시점 spike / rollback (3) 푸시 알림 실패 / 알림 인프라 5xx / 알림 채널 점검 | 활성화 시 30분 이상 또는 rate > 40% | `sum(rate(carry_dispatch_timeout_total[15m]))` (메트릭 도입 후) |
| `payment-failure-rate.md` | **CRITICAL** — 즉시 온콜 호출 (현재 placeholder); PG 상태 페이지 외부 확인; PG별 분해 `by(pg)` 검사 | (1) PG 장애 / 특정 `pg` 라벨의 failure spike / `resilience4j_circuitbreaker_state{name=~"pg-gateway.*"}` open 자연 fallback (2) PG API 키 만료 / 401 응답 / 환경변수 갱신 (3) 결제 금액 한도 초과 / 4xx 응답이 5%+ / 한도 정책 재검토 | 즉시 — critical은 사용자 영향 직접; 5분 firing이면 자동 호출 | `sum by(pg) (rate(carry_payment_failure_total[5m]))` |
| `kafka-dlq-nonempty.md` | DLQ 토픽의 메시지 헤더 (`kafka_dlt-original-topic`, `kafka_dlt-exception-message`) 확인 | (1) `DeserializationException` / `exception=DeserializationException` 라벨 / 이벤트 스키마 호환성 검증 (Phase 6.2 후속) (2) 영구 비즈니스 실패 / 동일 메시지 반복 DLQ 발행 / 메시지 수동 검토 (3) 일시적 외부 장애 / `RuntimeException` exception 라벨 / 외부 시스템 회복 대기 후 재처리 | DLQ accumulation > 100 또는 동일 exception 지속 | `sum by(topic, exception) (increase(carry_kafka_dlq_total[1h]))` |
| `hikari-pool-saturation.md` | `leak detection threshold` (5초) 경고 로그 grep; 슬로우 쿼리 식별 (Postgres `pg_stat_statements`) | (1) 트랜잭션 길이 폭증 / `hikaricp_connections_usage_seconds` p99 spike / 트랜잭션 경계 점검 (2) N+1 / 단일 요청 다수 short query / Repository fetch join 적용 (3) 외부 API 호출이 트랜잭션 내 / API 응답 시간 spike + Hikari pending 동반 / API 호출을 트랜잭션 외부로 이동 | active/max > 95% 5분 또는 `hikaricp_connections_pending > 0` | `hikaricp_connections_active / hikaricp_connections_max` (pool별) |
| `order-volume-drop.md` | 비즈니스 캠페인/이벤트 일정 확인 (외부); 앱(carry-app) 헬스체크 | (1) 앱·웹 장애 / `ApiHighErrorRate` 동시 firing / 카나리/롤백 (2) 결제 funnel drop / `PaymentHighFailureRate` 동시 / 결제 alert 우선 대응 후 자연 회복 (3) 외부 SNS·검색 채널 이슈 / 트래픽 source 분해 confirm / 채널별 PR 별도 대응 | 1시간 이상 < 50% 또는 < 30% drop | `sum(rate(carry_order_created_total[1h])) / sum(rate(carry_order_created_total[1h] offset 1d))` |

위 표를 그대로 각 런북 파일의 골격에 매핑해 작성. 표를 본문에 옮길 때는 의미가 살아있는 단락으로 풀어 쓴다(표 형태 그대로도 OK).

- [ ] **Step 3: 런북 8개 + README 작성 후 링크 검증**

```powershell
# 모든 룰의 runbook_url이 존재하는 파일을 가리키는지
$rules = Get-Content infra/prometheus/rules/carry-baseline.rules.yml -Raw
$urls = [regex]::Matches($rules, "runbook_url: '([^']+)'") | ForEach-Object { $_.Groups[1].Value }
$missing = 0
foreach ($url in $urls) {
    $path = $url -replace '.*/develop/', ''
    if (-not (Test-Path $path)) {
        Write-Host "MISSING: $path"
        $missing++
    }
}
"Missing: $missing of $($urls.Count)"
```
Expected: `Missing: 0 of 8`

- [ ] **Step 4: Commit**

```
docs(runbooks): Phase 3.5 — 8개 알럿 런북 + README + 수동 스모크 체크리스트
```

### Task 3.3: ROADMAP + 메모리 갱신

**Files:**
- Modify: `ROADMAP.md`
- Modify: `C:/Users/Eisen/.claude/projects/C--Users-Eisen-Desktop-Labs--projects--carry/memory/MEMORY.md`
- Modify: `C:/Users/Eisen/.claude/projects/C--Users-Eisen-Desktop-Labs--projects--carry/memory/carry-platform-roadmap-progress.md`
- Modify: `C:/Users/Eisen/.claude/projects/C--Users-Eisen-Desktop-Labs--projects--carry/memory/carry-platform-known-debts.md`

- [ ] **Step 1: ROADMAP Phase 3.5의 체크박스 갱신**

`ROADMAP.md` line 258(`- [ ] 알럿별 대응 절차 문서 작성`)을 `- [x]`로. Edit 도구로:
- old_string: `- [ ] 알럿별 대응 절차 문서 작성`
- new_string: `- [x] 알럿별 대응 절차 문서 작성`

Phase 3.4 본문(line 244~254)은 표 텍스트뿐이라 별도 체크박스 없음. 완료 표시는 다음 Step에서 진행 노트로 추가.

- [ ] **Step 1b: ROADMAP 끝에 진행 노트 섹션 추가**

`ROADMAP.md` 맨 끝(`---\n` 마지막 블록 다음)에 추가:
```markdown
---

## 진행 노트

- 2026-05-29: Phase 3.4(알럿 기준선) + 3.5(런북) 완료 — PR #<번호>
  - 8개 Prometheus alert rules + Alertmanager 라우팅 + 8개 런북
  - `carry_dispatch_timeout_total` 메트릭 부재로 룰 #4는 absent 가드 비활성 (known debt)
```

ROADMAP 파일이 untracked 상태이면 이 작업에서 함께 add. `git status`로 확인.

- [ ] **Step 2: `carry-platform-roadmap-progress.md` 메모리 업데이트**

내용을 다음으로 갱신 (절대 경로 사용):
```markdown
---
name: carry-platform-roadmap-progress
description: carry-platform ROADMAP 진행 상태 — 완료 Phase, 다음 후보
metadata:
  type: project
---

Phase 1·2·3.1·3.2·3.3·3.4·3.5 완료 (PR #55~<머지된 PR 번호 — 본 Step 직전에 `gh pr view --json number -q .number`로 조회해 채움>). 테스트 513/513 통과(Kotlin 변경 0). 다음 후보:
- Phase 4.1 역할 기반 인가 (CUSTOMER/CARRIER/COORDINATOR/ADMIN 도입 — Spring Security `@PreAuthorize`)
- 또는 known-debts의 [[carry-platform-known-debts]] 항목 중 dispatch.timeout 메트릭(룰 #4 활성화) 정리
```

- [ ] **Step 3: `carry-platform-known-debts.md` 메모리에 신규 항목 추가**

기존 본문 끝에 추가:
```markdown
- 2026-05-29 Phase 3.4: `carry_dispatch_timeout_total` 메트릭 미구현 — 룰 #4(DispatchTimeoutRate)는 absent 가드로 비활성. Dispatch 자동 만료 스케줄러 + `markTimedOut()` 카운터 호출 도입 시 자동 활성화.
- 2026-05-29 Phase 3.4: 실 Slack/PagerDuty 미연결 — placeholder URL + env 주입 경로만 마련. 실 운영 전환 시 env 주입만으로 활성화.
- 2026-05-29 Phase 3.4: branch protection에 `validate-alerts` required check 미등록 — 후속 PR로 등록.
```

- [ ] **Step 4: `MEMORY.md` 인덱스는 변경 불필요** (기존 항목들 그대로 유효)

- [ ] **Step 5: Commit**

```
docs(roadmap): Phase 3.4·3.5 완료 표시 + 알려진 빚 갱신
```

### Task 3.4: PR 생성 + 자율 머지

- [ ] **Step 1: 현재 브랜치 push**

```powershell
git push -u origin feature/alert-baseline-and-runbooks
```

- [ ] **Step 2: PR 본문 작성 후 PR 생성**

PR 본문을 `$env:TEMP\carry-pr-body.md`에 작성한 뒤 `gh pr create`의 `--body-file`로 전달 (repo 안에 임시 파일 두지 않음). 본문 구조:

```markdown
## 배경

ROADMAP Phase 3.4(알럿 기준선) + 3.5(운영 런북) — Phase 3.2(Prometheus+Grafana)와 3.3(Saga MDC) 후속. 메트릭은 흐르지만 사람에게 도달하는 알럿 경로 부재를 해소.

## 변경 사항

- **알럿 룰** 8개 — `infra/prometheus/rules/carry-baseline.rules.yml`
- **promtool 단위 테스트** 16 케이스 — `infra/prometheus/rules/tests/carry-baseline.test.yml`
- **Alertmanager 라우팅** — `infra/alertmanager/alertmanager.yml`, 3 receiver
- **docker-compose** — alertmanager + alert-webhook-logger 서비스 2개 추가
- **Grafana 패널** — "Active firing alerts" stat 패널 (id=15)
- **GitHub Actions** — `.github/workflows/validate-alerts.yml` (promtool check/test + amtool + 런북 링크)
- **런북 8개** — `docs/operations/runbooks/`
- **ROADMAP & 메모리** — Phase 3.4·3.5 완료 표시, known-debts 3건 추가
- **Kotlin 변경: 0줄** (회귀 0)

## 알럿 카탈로그

| # | Alert | Severity | Route |
|---|---|---|---|
| 1 | ApiHighLatencyP99 | warning | slack |
| 2 | ApiHighErrorRate | warning | slack |
| 3 | KafkaConsumerLag | warning | slack |
| 4 | DispatchTimeoutRate ⚠ | warning | business |
| 5 | PaymentHighFailureRate | critical | oncall |
| 6 | KafkaDlqNonEmpty | warning | slack |
| 7 | HikariPoolSaturation | warning | slack |
| 8 | OrderVolumeDropDoD | warning | business |

## 검증

- ✅ promtool check rules
- ✅ promtool test rules — 16/16
- ✅ amtool check-config
- ✅ runbook link check — 8/8 paths resolve
- ✅ Gradle build + test 513/513 (Kotlin 변경 0)
- ✅ 수동 스모크: Prometheus rules 페이지 / Alertmanager status / webhook-logger 로그

(스크린샷 첨부)

## 알려진 빚

1. `carry_dispatch_timeout_total` 메트릭 부재 — 룰 #4는 absent 가드로 비활성. Phase 2 후속 PR
2. 실 Slack/PagerDuty 미연결 — env 주입 경로만 마련
3. branch protection의 required check에 `validate-alerts` 미등록

## ROADMAP 진행

- [x] Phase 3.4 알럿 기준선
- [x] Phase 3.5 운영 런북

다음 후보: Phase 4.1 역할 기반 인가, 또는 known-debts의 dispatch.timeout 메트릭 정리.
```

PR 생성 명령:
```powershell
gh pr create --base develop --head feature/alert-baseline-and-runbooks `
  --title "feat(observability): ROADMAP Phase 3.4 — 알럿 기준선 + 운영 런북" `
  --body-file "$env:TEMP\carry-pr-body.md"
```

- [ ] **Step 3: 자율 머지**

```powershell
gh pr merge --auto --merge --delete-branch
```

`--auto`로 CI(`build.yml` + `validate-alerts.yml`) 통과 후 자동 머지, `--delete-branch`로 브랜치 정리. develop에 stack 자식 PR이 없으므로 reparent 불필요.

- [ ] **Step 4: 머지 후 develop 동기화**

```powershell
git switch develop
git pull --ff-only
```

- [ ] **Step 5: 머지 완료 확인 — 최신 commit이 PR 머지 커밋인지**

```powershell
git log --oneline -3
```
Expected: 최상단에 머지 커밋(`Merge pull request #<번호> ...`).

---

## Plan 완료 기준

- [ ] Chunk 1·2·3 모두 완료
- [ ] PR 머지됨
- [ ] develop 로컬 동기화
- [ ] 메모리 3건 갱신 확인

## 실패 시 회복 (rollback)

- 룰 작성 중 promtool test가 풀리지 않으면 → 해당 룰의 input_series 시계열 길이/간격 미세조정 (특히 #8 DoD는 25h+ 시계열 필요). 막히면 #8을 별 PR로 분리하고 #1~#7 + 런북만 우선 머지.
- docker-compose 부팅 실패 시 → `docker compose logs <service>`로 원인 식별. webhook-logger 이미지 호환성 문제면 `mendhak/http-https-echo`를 `traefik/whoami` 또는 `kennethreitz/httpbin`으로 대체.
- CI에서 promtool check fail 시 → 로컬에서 동일 Docker 명령으로 재현 후 수정.
