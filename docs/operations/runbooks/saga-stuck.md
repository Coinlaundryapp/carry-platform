# SagaStuck

## 개요

- **Alert ID:** `SagaStuck`
- **Severity:** warning
- **Route:** business (`#carry-business`)
- **트리거 조건:** `StuckSagaDetector` 가 비종결 중간 상태(CREATED·DISPATCHED·PICKED_UP·INVOICED·
  PAID·IN_PROGRESS·REFUND_PENDING)에 임계(기본 6h) 이상 정체된 주문을 감지해
  `carry_saga_stuck_total{status}` 을 증가시킴 → 30분 증분 > 0
- **for:** 5m
- **룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **소스:** `carry-order` `StuckSagaDetector`

## 무엇이 일어나고 있는가

주문 사가가 어떤 중간 상태에서 6시간 넘게 진행되지 않고 멈춰 있다. 본 시스템은 Choreography
Saga(ADR-0004)로, 정상이라면 각 단계가 이벤트로 다음 단계를 트리거한다. 정체는 보통 외부 트리거가
오지 않거나(예: 결제·세탁소 처리 지연) 특정 단계의 이벤트 소비가 실패해 멈춘 경우다.

> 참고: 알려진 정체 원인(배차 미수락·재결제 시한)은 전용 스위퍼가 자동 종결한다
> (`DispatchTimeoutSweeper`, `PaymentRetryDeadlineSweeper`). 본 알럿은 그 외의 정체를 가시화하는
> **안전망**이다 — 자동 복구가 아니라 운영 개입 신호.

## 즉시 확인할 것 (5분 이내)

1. **어느 status 가 정체인가** — 알럿 라벨 `status` 로 단계 파악(예: INVOICED = 청구 후 결제 안 됨).
2. **건수·추이** — `curl localhost:8080/actuator/prometheus | grep carry_saga_stuck` 또는
   Grafana 에서 status 별 추이. 단발인지 누적인지 확인.
3. **해당 주문 식별** — DB 에서 `orders` 의 비종결 상태 + `updated_at` 이 오래된 행 조회:
   ```sql
   SELECT id, status, updated_at FROM orders
   WHERE status IN ('CREATED','DISPATCHED','PICKED_UP','INVOICED','PAID','IN_PROGRESS','REFUND_PENDING')
     AND updated_at < now() - interval '6 hours'
   ORDER BY updated_at;
   ```
4. **사가 로그 추적** — 해당 `orderId` 로 `SagaLogContext`(MDC orderId) 로그를 따라가 어느 이벤트가
   안 왔는지/소비 실패했는지 확인. DLQ(`KafkaDlqNonEmpty`) 동시 firing 여부 확인.

## 흔한 원인 → 대응

| status | 흔한 원인 | 대응 |
|---|---|---|
| CREATED | OrderCreatedEvent 소비 실패 → Dispatch 미생성 | 컨슈머 로그·DLQ 확인. 이벤트 재처리 또는 주문 취소 |
| DISPATCHED | 기사 픽업 지연/누락 | 운영팀이 기사 컨택. 장기 시 수동 취소 |
| INVOICED | 고객 결제 미완료 | 재결제 시한 스위퍼가 24h 후 종결. 그 전 정체는 고객 안내 |
| PAID / IN_PROGRESS | 세탁소·배달 단계 이벤트 누락 | 해당 모듈(delivery) 로그·이벤트 소비 확인 |
| REFUND_PENDING | RefundCompletedEvent 미수신 | 결제 게이트웨이 환불 상태·PaymentSaga 로그 확인 |

## 에스컬레이션 기준

- 같은 주문이 24시간 넘게 정체(스위퍼·재시도로도 해소 안 됨)
- 다수 주문이 동일 status 에 몰림(특정 단계 이벤트 파이프라인 장애 의심) → `KafkaDlqNonEmpty`·
  `KafkaConsumerLag` 동반 확인 후 인프라 에스컬레이션

## 관련 PromQL

```promql
# status 별 정체 감지 증분 (30분)
sum by(status) (increase(carry_saga_stuck_total[30m]))
```

## 관련 ADR/메트릭

- [ADR-0004 Choreography Saga](../../adr/0004-choreography-saga.md) — 사가 상태가 애그리거트에 분산되는 구조
- `StuckSagaDetector` (비파괴 감지), `DispatchTimeoutSweeper`·`PaymentRetryDeadlineSweeper` (자동 종결)
