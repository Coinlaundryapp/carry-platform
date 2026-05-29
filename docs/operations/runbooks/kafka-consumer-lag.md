# KafkaConsumerLag

## 개요

- **Alert ID:** `KafkaConsumerLag`
- **Severity:** warning
- **Route:** slack (`#carry-alerts`)
- **트리거 조건:** 토픽별 컨슈머 lag 합이 1000 초과 (10분 지속)
- **for:** 10m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

특정 토픽의 컨슈머 lag가 1000개 이상으로 10분 이상 지속. 이벤트 처리가 생산 속도를 따라가지
못하는 상태. Saga(Order/Dispatch/Delivery/Payment/Notification)의 일부 단계가 지연되어
사용자에게 "처리 중" 화면이 길어지거나 자동 만료 로직이 늦게 실행될 수 있다.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience`에서 "Kafka consumer lag" 패널 확인.
   firing 중인 토픽(`{{ $labels.topic }}`) 식별.
2. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 알럿 확인
   (`HikariPoolSaturation`·`KafkaDlqNonEmpty` 동반 여부).
3. **Saga 상관관계**: `correlationId` MDC로 lag 토픽 관련 saga 단계 로그 추적.
4. **kafka-consumer-groups 확인** (직접 진단):
   ```bash
   kafka-consumer-groups.sh --describe \
     --bootstrap-server localhost:9092 \
     --group <consumer-group-name>
   ```
   파티션별 LAG·CONSUMER-ID 확인. 특정 파티션만 큰지 또는 컨슈머 미할당(LAG=*)인지 식별.
5. **컨슈머 컨테이너 헬스** — `docker ps` 또는 `kubectl get pods`에서 restart count 확인.
   OOMKilled 반복이면 heap 또는 max-poll-records 조정 필요.
6. **DB 응답 시간** — 컨슈머가 DB 쓰기에 막혀 있는지. `HikariPoolSaturation` 동반이면 거의 확실.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| DB 쓰기 지연 | `HikariPoolSaturation` 동시 firing, `hikaricp_connections_pending > 0` | pool size 증가 또는 슬로우 쿼리 튜닝. 트랜잭션 경계 점검 |
| 외부 API 실패 | `resilience4j_circuitbreaker_state{state="open"}`, 외부 호출 카운터 spike | CB 자연 fallback 대기. 외부 의존성 회복 시 lag 자동 해소 |
| 컨슈머 OOM 재시작 | `kafka_consumer_records_consumed_total` 평탄 + 컨테이너 restart 카운트 증가 | JVM heap 증가 또는 `max-poll-records` 축소(500→100). poll 사이 처리 부하 분산 |

## 에스컬레이션 기준

- 30분 이상 지속 또는 lag > 10000
- `payment.command` 또는 `order.command` 토픽 lag firing (사용자 직접 영향)
- DLQ 메시지 동시 발생 (`KafkaDlqNonEmpty` firing)

## 관련 메트릭/대시보드 패널

- Grafana 패널: "Kafka consumer lag (by topic)"
- 상세 PromQL:
  ```promql
  # 토픽·컨슈머그룹별 lag — 가장 막힌 구간 식별
  sum by(topic, consumergroup) (kafka_consumer_fetch_manager_records_lag)
  ```
- 처리율 진단:
  ```promql
  # 토픽별 컨슈머 처리율 (records/s) — 떨어지면 컨슈머 측 문제
  sum by(topic) (rate(kafka_consumer_fetch_manager_records_consumed_total[5m]))
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 2.1 Kafka DLQ + retry (PR #56, #57)
- known-debts: EmbeddedKafka 테스트 격리 (Phase 2.1 후속)
- known-debts: Outbox 발행 영역 e2e 검증
