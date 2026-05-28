# 12. 관측성 스택 — Prometheus & Grafana

> 최종 수정일: 2026-05-29
> 상태: Phase 3.2 — Active

ROADMAP Phase 3.2 산출물. `docker-compose.yml`에 Prometheus와 Grafana를 추가해, `MetricsPort`(`docs/11-business-metrics.md`)로 발행되는 메트릭과 Spring Boot Actuator 표준 메트릭을 실시간으로 본다.

---

## 구성

```
┌────────────────────────────┐   pull /actuator/prometheus
│ carry-platform (호스트:8080) │ ◀────────────────┐
└────────────────────────────┘                   │
                                                 │
┌─────────────────────────────────────────────┐  │
│ docker-compose                              │  │
│                                             │  │
│  ┌────────────┐     scrape (15s)            │  │
│  │ prometheus │ ─────────────────────────────┘
│  │   :9090    │
│  └─────┬──────┘
│        │ datasource (proxy)
│        ▼
│  ┌────────────┐
│  │  grafana   │ — http://localhost:3000 (admin/admin)
│  │   :3000    │
│  └────────────┘
│
│  ┌─────────────────┐    OTLP traces only
│  │ otel-collector  │ ───────────────► jaeger:16686
│  │   :4317/:4318   │
│  └─────────────────┘
└─────────────────────────────────────────────┘
```

OTel Collector의 `prometheus` exporter(8889)는 dead path로 남겨둔다(현재 앱은 OTLP로 메트릭을 push하지 않음 — 트레이싱만 OTLP). 추후 OTel SDK auto-instrumentation을 도입하면 `infra/prometheus.yml`의 주석 처리된 scrape job을 켜면 된다.

---

## 기동 / 접속

```bash
docker compose up -d prometheus grafana
# 또는 전체:
docker compose up -d
```

| 서비스 | URL | 자격 |
|---|---|---|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin (Viewer는 익명 가능) |
| Jaeger | http://localhost:16686 | — |

대시보드는 자동 프로비저닝되어 `Carry / Carry — Business & Resilience`로 즉시 보인다.

### 호스트→컨테이너 통신

Prometheus는 컨테이너에서 호스트(`localhost:8080`의 carry-platform)를 스크레이프해야 하므로 `host.docker.internal`을 사용한다. Docker Desktop(Windows/Mac)은 기본 제공, Linux Docker Engine은 docker-compose에 명시한 `extra_hosts: ["host.docker.internal:host-gateway"]`로 동등한 효과.

---

## 기본 대시보드 — `Carry — Business & Resilience`

| 섹션 | 패널 | 메트릭 |
|---|---|---|
| **주문 라이프사이클 퍼널** | 주문 생성 / 배차 수락 (by via) / 배달 완료 / 결제 성공률 (Stat) | `carry_order_created_total`, `carry_dispatch_accepted_total{via=...}`, `carry_delivery_completed_total`, `carry_payment_success_total / (success+failure)` |
| | 퍼널 흐름 (Timeseries) | 위 4종 한 차트에 |
| **장애 신호** | 결제 실패 (by pg) | `carry_payment_failure_total{pg=...}` |
| | 배차 거부율 | `carry_dispatch_rejected_total / (accepted+rejected)` |
| | Kafka DLQ 적재 (by topic) | `increase(carry_kafka_dlq_total[5m])` |
| | Outbox 적재 (gauge) | `carry_outbox_pending` |
| **Circuit Breaker & 인프라** | CB OPEN 상태 stat | `resilience4j_circuitbreaker_state{name=~"pg-gateway.*\|geocoding.*", state="open"}` |
| | API p50/p95/p99 | `http_server_requests_seconds_bucket` |
| | HikariCP 풀 사용률 | `hikaricp_connections_{active,idle,max,pending}{pool="CarryHikariPool"}` |
| | Kafka Consumer Lag (by topic) | `kafka_consumer_fetch_manager_records_lag` |
| | 배달 소요 시간 p50/p95 | `carry_delivery_duration_seconds_bucket` |

> Prometheus의 자동 접미사 규칙: Micrometer Counter `carry.order.created` → `carry_order_created_total`, Timer `carry.delivery.duration` → `carry_delivery_duration_seconds_*`.

---

## 대시보드 편집 정책

- **Provisioning 우선**: `infra/grafana/dashboards/*.json`이 정본. UI에서 수정한 변경은 Grafana 재시작 시 덮어쓰인다(`allowUiUpdates: false`).
- **편집 흐름**: Grafana UI에서 시안을 만든 뒤 JSON 모델을 export → 저장소의 `.json`으로 커밋 → 재기동 시 자동 반영.
- **시크릿 분리**: 데이터소스에 비밀이 들어가는 경우 `infra/grafana/provisioning/datasources/*.yml`에 환경변수 보간(`$ENV_VAR`)을 쓰고 `.env`로 주입.

---

## 데이터 유지

| 볼륨 | 보존 |
|---|---|
| `carry-prometheus-data` | 15일 (`--storage.tsdb.retention.time=15d`) |
| `carry-grafana-data` | 영구 (대시보드 변경 이력, 사용자) |

`docker compose down -v`는 모든 메트릭 히스토리를 삭제한다. 로컬 실험 데이터는 휘발성.

---

## 다음 단계 (Phase 3.3 / 3.4 / 3.5)

- **3.3 로깅 강화** — Saga 핸들러에 `MDC.put("orderId", ...)` 적용, 4xx/5xx/비즈니스 예외 레벨 분류.
- **3.4 알럿 기준선** — Prometheus alert rules YAML 추가. `infra/prometheus.yml`에 `rule_files:`로 연결. 추천 시작 임계값은 `docs/11-business-metrics.md`의 "Phase 3.4 알럿 기준선 연계" 섹션 참조.
- **3.5 운영 런북** — 알럿별 대응 절차. `docs/13-runbook.md` 신설 예정.
