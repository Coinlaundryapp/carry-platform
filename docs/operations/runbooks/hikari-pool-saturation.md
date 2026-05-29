# HikariPoolSaturation

## 개요

- **Alert ID:** `HikariPoolSaturation`
- **Severity:** warning
- **Route:** slack (`#carry-alerts`)
- **트리거 조건:** HikariCP active/max 비율이 80% 초과 (10분 지속)
- **for:** 10m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

DB 커넥션 풀(`CarryHikariPool`, max=20)의 80% 이상이 활성 상태로 10분 이상 지속.
신규 요청이 커넥션 획득에 대기하기 시작하면 응답시간 폭증과 5xx로 이어진다. Phase 2.3에서
정의한 `connection-timeout=3s`에 걸리면 즉시 실패하므로 `ApiHighErrorRate` 동반 가능성 높음.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience`의 "HikariCP 사용률" 및 pending 패널 확인.
   active/max 추이와 pending 발생 여부.
2. **Alertmanager UI**에서 동시 firing 알럿 확인.
   `ApiHighLatencyP99`·`ApiHighErrorRate`·`KafkaConsumerLag` 동반 거의 확실.
3. **Leak detection 5s 경고 로그 확인** — Phase 2.3에서 `leak-detection-threshold=5s` 설정.
   ```
   grep -i "leak" /var/log/carry-platform/app.log | tail -20
   ```
   "Apparent connection leak detected" 메시지가 있으면 커넥션 close 누락된 코드 식별 가능.
4. **Saga 상관관계**: `correlationId` MDC로 장시간 점유 중인 트랜잭션 로그 추적.
5. **슬로우 쿼리 식별** — Postgres `pg_stat_statements` 또는 RDS Performance Insights:
   ```sql
   SELECT query, calls, mean_exec_time, total_exec_time
   FROM pg_stat_statements
   ORDER BY mean_exec_time DESC LIMIT 20;
   ```
6. **현재 active 트랜잭션** — `SELECT * FROM pg_stat_activity WHERE state='active';`로 장기 실행 트랜잭션 식별.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| 트랜잭션 길이 폭증 | `hikaricp_connections_usage_seconds` p99 spike, pg_stat_activity에서 5s+ 트랜잭션 | 트랜잭션 경계 점검(`@Transactional` 범위 축소). 외부 API 호출이 트랜잭션 내부에 있으면 외부로 이동 |
| N+1 쿼리 | 단일 요청에서 수십·수백 short 쿼리, `pg_stat_statements`에서 동일 쿼리 calls 폭증 | Repository에 fetch join 적용 또는 `@EntityGraph`. 가능하면 `@BatchSize` |
| 외부 API 호출이 트랜잭션 내 | API 응답시간 spike + Hikari pending 동반, CB 상태 OPEN | API 호출을 트랜잭션 외부로 이동. saga·이벤트 발행으로 분리 (Outbox 패턴 활용) |

## 에스컬레이션 기준

- active/max > 95% 5분 또는 `hikaricp_connections_pending > 0`
- `ApiHighErrorRate` 동시 firing (커넥션 timeout이 5xx로 표면화)
- 30분 이상 지속 → 임시 pool size 증가 (env `HIKARI_POOL_SIZE`) 검토하되 근본 원인 해결 필수

## 관련 메트릭/대시보드 패널

- Grafana 패널: "HikariCP 사용률" (pool별 active·idle·max·pending)
- 상세 PromQL:
  ```promql
  # 풀별 사용률 — 본 룰과 동일 expression
  hikaricp_connections_active{pool="CarryHikariPool"}
    / hikaricp_connections_max{pool="CarryHikariPool"}
  ```
- 진단:
  ```promql
  # pending — 0 초과면 즉시 대응 필요
  hikaricp_connections_pending{pool="CarryHikariPool"}

  # 평균 점유 시간 — leak/슬로우 쿼리 신호
  rate(hikaricp_connections_usage_seconds_sum[5m])
    / clamp_min(rate(hikaricp_connections_usage_seconds_count[5m]), 0.001)
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 2.3 HikariCP 튜닝 (PR #59) — pool-size=20, connection-timeout=3s, leak-detection 5s
- ROADMAP Phase 2.5 `@Version` 동시성 (PR #60) — 옵티미스틱 락 충돌 시 트랜잭션 재시도 발생
- Phase 6.4 known-debts: `Order.reconstitute()` 파라미터 정리와 함께 트랜잭션 경계 재검토
