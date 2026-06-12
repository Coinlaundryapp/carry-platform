# KafkaDlqNonEmpty

## 개요

- **Alert ID:** `KafkaDlqNonEmpty`
- **Severity:** warning
- **Route:** slack (`#carry-alerts`)
- **트리거 조건:** 토픽별 DLQ 5분 증분이 0 초과 (1분 유지)
- **for:** 1m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

원본 토픽 처리 중 3회 재시도(Phase 2.1 FixedBackOff 1초·3회)가 모두 실패해 `{원본}.DLQ`
토픽으로 메시지가 발행됨. 비즈니스 이벤트 1건이 영구 실패한 상태이거나, 직렬화/역직렬화 호환성
이슈, 또는 외부 의존성 장시간 장애의 신호.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience`의 "Kafka DLQ" 패널에서 firing 토픽(`{{ $labels.topic }}`)과 증분 추이 확인
2. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 알럿 확인.
   `KafkaConsumerLag`·`ApiHighErrorRate`·`PaymentHighFailureRate` 동반 여부.
3. **Saga 상관관계**: `correlationId` MDC로 DLQ 발행 시점 직전 ERROR 로그 추적.
   `DeadLetterPublishingRecoverer`의 발행 로그에서 원본 메시지 키 식별.
4. **DLQ 메시지 헤더 확인** — `kafka-console-consumer` 또는 Kafka UI로 DLQ 토픽에서 헤더 추출:
   - `kafka_dlt-original-topic`
   - `kafka_dlt-exception-class-name`
   - `kafka_dlt-exception-message`
   - `kafka_dlt-original-partition`, `kafka_dlt-original-offset`
5. **예외 타입 분해** — 동일 exception이 반복인지 (영구 실패), 다양한 exception인지(직렬화 문제 가능성).
6. **외부 의존성 상태** — DB·외부 API CB 상태 확인. 일시 장애였으면 회복 후 수동 재처리 가능.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| `DeserializationException` | `exception=DeserializationException` 라벨, 특정 토픽 모든 메시지가 DLQ로 | 이벤트 스키마 호환성 검증 (Phase 6.2 이벤트 진화 정책 후속). 발행자/소비자 버전 미스매치면 발행자 즉시 rollback |
| 영구 비즈니스 실패 | 동일 메시지·키가 반복 DLQ 발행, 비즈니스 예외(`BusinessException`) 메시지 | 메시지 수동 검토 후 폐기 또는 보상 트랜잭션 수동 실행. 비즈니스 규칙 위반 패턴이면 발행 시점 검증 강화 |
| 일시적 외부 장애 | `RuntimeException`(IO/Timeout) 라벨, 단발성 증분 | 외부 시스템 회복 대기 후 DLQ 수동 재처리(known-debts: DLQ 재처리 메커니즘). retry 횟수 임시 증가 검토 |

## 에스컬레이션 기준

- DLQ accumulation > 100 또는 동일 exception 지속(2회 이상 다른 시간대)
- `payment.command`·`order.command` 토픽 DLQ 발행 (사용자 직접 영향 가능성)
- `DeserializationException` 광범위 발생 (스키마 회귀 신호)

## 관련 메트릭/대시보드 패널

- Grafana 패널: "Kafka DLQ (by topic, exception)"
- 상세 PromQL:
  ```promql
  # 토픽·예외별 1시간 누적 발행 — 영구 실패 패턴 식별
  sum by(topic, exception) (
    increase(carry_kafka_dlq_total[1h])
  )
  ```
- 진단:
  ```promql
  # 5분 증분 (룰과 동일) — 실시간 발행 추이
  sum by(topic) (increase(carry_kafka_dlq_total[5m]))
  ```

## DLQ 재처리 (redrive)

원인 해소를 확인한 후(위 절차 선행), ADMIN 토큰으로 재처리 엔드포인트를 호출한다:

```bash
# 검토: DLQ 토픽 메시지 dump
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic <원본>.DLQ --from-beginning --max-messages 10 \
  --property print.headers=true --property print.key=true

# 재처리: 최대 maxRecords건을 원본 토픽으로 재발행 (감사 로그 기록됨)
curl -X POST http://localhost:8080/api/v2/admin/dlq/redrive \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"topic": "<원본 토픽명>", "maxRecords": 100}'
# → {"data": {"redriven": N, "parked": M}}
```

동작 규칙(`DlqRedriveService`):
- 재발행마다 `carry_dlq-redrive-count` 헤더 증가. **3회 도달 메시지는 보류(parked)** —
  재발행되지 않고 DLQ에 남는다(poison 메시지 무한 순환 차단). `parked > 0`이면 해당
  메시지를 수동 검토 후 폐기 또는 보상 트랜잭션을 직접 실행한다.
- 오프셋은 처리 성공분까지만 커밋 — 중간 실패 시 미처리분은 다음 호출에서 이어진다.
- 메트릭: `carry_kafka_dlq_redriven_total`, `carry_kafka_dlq_parked_total` (topic 라벨).

## 관련 ROADMAP/known-debts

- ROADMAP Phase 2.1 Kafka DLQ + retry 전략 (PR #56, #57)
- DLQ 재처리 메커니즘 — 해소됨 (`POST /api/v2/admin/dlq/redrive`, 위 절 참조)
- Outbox 발행 e2e 검증 — 해소됨 (`OutboxAtomicityIntegrationTest`, PR #123)
- ROADMAP Phase 6.2 이벤트 스키마 진화 전략
