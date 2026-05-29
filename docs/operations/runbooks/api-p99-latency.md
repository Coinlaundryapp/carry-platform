# ApiHighLatencyP99

## 개요

- **Alert ID:** `ApiHighLatencyP99`
- **Severity:** warning
- **Route:** slack (`#carry-alerts`)
- **트리거 조건:** API 전체 p99 응답시간이 5분 평균 3초 초과
- **for:** 5m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

API 요청의 99 백분위 응답시간이 3초를 초과하는 상태가 5분 이상 지속됨. 사용자 체감 응답이
느려지고, 상위 1% 요청에서 타임아웃·재시도 폭이 확대될 수 있다. 모바일 클라이언트의 기본
타임아웃(보통 10s)에 근접하면 사용자 측에서 실패로 인지된다.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience` (http://localhost:3000/d/carry-business)
   에서 "API p99 응답시간" 패널 및 동 패널 옆 throughput 패널 확인
2. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 알럿 확인
   (`HikariPoolSaturation`·`KafkaConsumerLag`·`ApiHighErrorRate` 동반 여부)
3. **Saga 상관관계**: Phase 3.3에서 도입한 `correlationId` MDC로 느린 요청 로그 추적
4. **직전 배포 확인** — `git log --oneline origin/develop -10`으로 지난 1시간 내 머지된 commit/tag.
   카나리 또는 롤백 후보 식별.
5. **CB 상태 확인** — `resilience4j_circuitbreaker_state{state="open"}` 쿼리.
   `pg-gateway-*`·`geocoding-*` CB가 OPEN이면 외부 의존 지연이 원인.
6. **GC pause 확인** — `histogram_quantile(0.99, sum by(le)(rate(jvm_gc_pause_seconds_bucket[5m])))`
   spike 여부.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| DB 슬로우 쿼리 | Hikari leak detection 5s 경고 로그, `hikaricp_connections_pending > 0` | EXPLAIN ANALYZE로 쿼리 식별 → 인덱스 추가 또는 N+1 fetch join 적용 |
| 외부 API 지연 | `resilience4j_circuitbreaker_state{name=~"pg-gateway.*"}` 또는 `geocoding-*` OPEN | Phase 2.2 CB 자연 fallback 대기. 30분 이상이면 외부 벤더 상태 페이지 확인 |
| GC pause 폭증 | `jvm_gc_pause_seconds` p99 spike, 컨테이너 메모리 사용량 90%+ | ZGC GC 로그 확인. 힙 부족이면 JVM `-Xmx` 증가, 메모리 누수면 heap dump |

## 에스컬레이션 기준

- 30분 이상 firing 또는 p99 > 5s
- `ApiHighErrorRate` 또는 `PaymentHighFailureRate` 동시 firing
- 모바일 앱에서 사용자 컴플레인 보고 (Slack `#carry-cs`)

## 관련 메트릭/대시보드 패널

- Grafana 패널: "API p99 응답시간" (carry-business 대시보드 내)
- 상세 PromQL:
  ```promql
  # URI별 p99 — 어느 엔드포인트가 느린지 식별
  histogram_quantile(0.99,
    sum by(le, uri) (rate(http_server_requests_seconds_bucket[5m]))
  )
  ```
- 추가 진단:
  ```promql
  # 메서드별 throughput
  sum by(uri, method) (rate(http_server_requests_seconds_count[5m]))
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 3.4 (본 알럿 기준선)
- known-debts: Geocoding CB graceful degradation, PG fallback 큐
- Phase 2.2 Circuit Breaker (PR #61, #62)
