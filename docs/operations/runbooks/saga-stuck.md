# SagaStuck

## 개요

- **Alert ID:** `SagaStuck`
- **Severity:** warning
- **Route:** business (`#carry-business`)
- **트리거 조건:** `StuckSagaDetector` 가 비종결 중간 상태(CREATED·DISPATCHED·PICKED_UP·
  IN_PROGRESS)에 임계(기본 24h) 이상 정체된 주문을 감지해
  `carry_saga_stuck_total{status}` 을 증가시킴 → 30분 증분 > 0
- **for:** 5m
- **룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **소스:** `carry-order` `StuckSagaDetector`

## 무엇이 일어나고 있는가

주문 사가가 어떤 중간 상태에서 24시간 넘게 진행되지 않고 멈춰 있다. 본 시스템은 Choreography
Saga(ADR-0004)로, 정상이라면 각 단계가 이벤트로 다음 단계를 트리거한다. 정체는 보통 외부 트리거가
오지 않거나(예: 세탁소 처리 지연) 특정 단계의 이벤트 소비가 실패해 멈춘 경우다.

> **결제(과금) 실패는 이 알럿과 무관하다.** 결제·물리 흐름이 완전히 분리된 이후 주문은 과금 성공
> 여부와 무관하게 COMPLETED 까지 진행하며, `OrderStatus` 는 물리 상태(CREATED/DISPATCHED/PICKED_UP/
> IN_PROGRESS/COMPLETED/CANCELLED)만 표현한다. 과금 실패는 주문을 종결하지 않는다 — 결제 재시도는
> `ChargeRetrySweeper`(백오프 재과금), 연체(72h 미결제)는 `OverdueSweeper`(신규 주문만 차단) 소관이며
> 둘 다 carry-payment 모듈의 독립된 스위퍼다.
>
> 감시 대상은 모두 **물리 작업 상태**라 정상 주문도 체류 시간이 길다(PICKED_UP 은 캐리어가 세탁을
> 수동 시작할 때까지, IN_PROGRESS 는 세탁→건조→배달까지 몇 시간이 걸린다) — 그래서 임계를 24h 로
> 크게 잡는다. 알려진 정체 원인(배차 미수락)은 전용 스위퍼가 자동 종결한다(`DispatchTimeoutSweeper`).
> 본 알럿은 그 외의 정체를 가시화하는 **안전망**이다 — 자동 복구가 아니라 운영 개입 신호.

## 즉시 확인할 것 (5분 이내)

1. **어느 status 가 정체인가** — 알럿 라벨 `status` 로 단계 파악(예: PICKED_UP = 수거 후 세탁 미시작).
2. **건수·추이** — `curl localhost:8080/actuator/prometheus | grep carry_saga_stuck` 또는
   Grafana 에서 status 별 추이. 단발인지 누적인지 확인.
3. **해당 주문 식별** — DB 에서 `orders` 의 비종결 상태 + `updated_at` 이 오래된 행 조회:
   ```sql
   SELECT id, status, updated_at FROM orders
   WHERE status IN ('CREATED','DISPATCHED','PICKED_UP','IN_PROGRESS')
     AND updated_at < now() - interval '24 hours'
   ORDER BY updated_at;
   ```
4. **사가 로그 추적** — 해당 `orderId` 로 `SagaLogContext`(MDC orderId) 로그를 따라가 어느 이벤트가
   안 왔는지/소비 실패했는지 확인. DLQ(`KafkaDlqNonEmpty`) 동시 firing 여부 확인.
5. **결제 상태와 혼동하지 말 것** — 이 알럿은 물리 흐름만 본다. 해당 주문의 결제 상태가 궁금하면
   `carry-payment`의 `Invoice`/`Payment` 를 `orderId` 로 별도 조회한다(과금 실패·연체는 이 알럿을 울리지 않는다).

## 흔한 원인 → 대응

| status | 흔한 원인 | 대응 |
|---|---|---|
| CREATED | OrderCreatedEvent 소비 실패 → Dispatch 미생성 | 컨슈머 로그·DLQ 확인. 이벤트 재처리 또는 주문 취소 |
| DISPATCHED | 기사 픽업 지연/누락 | 운영팀이 기사 컨택. 장기 시 수동 취소 |
| PICKED_UP | 세탁 시작(`LaundryStartedEvent`) 이벤트 누락 또는 세탁소 처리 지연 | 해당 모듈(delivery) 로그·이벤트 소비 확인. 결제(과금) 상태는 무관 — 확인 불필요 |
| IN_PROGRESS | 배달 완료(`DeliveryCompletedEvent`) 이벤트 누락 또는 배달 지연 | 해당 모듈(delivery) 로그·이벤트 소비 확인 |

## 에스컬레이션 기준

- 같은 주문이 48시간 넘게 정체(임계 24h 를 2배 초과 — 재시도로도 해소 안 됨)
- 다수 주문이 동일 status 에 몰림(특정 단계 이벤트 파이프라인 장애 의심) → `KafkaDlqNonEmpty`·
  `KafkaConsumerLag` 동반 확인 후 인프라 에스컬레이션

## 관련 PromQL

```promql
# status 별 정체 감지 증분 (30분)
sum by(status) (increase(carry_saga_stuck_total[30m]))
```

## 관련 ADR/메트릭

- [ADR-0004 Choreography Saga](../../adr/0004-choreography-saga.md) — 사가 상태가 애그리거트에 분산되는 구조
- [06-saga.md](../../06-saga.md) — 결제·물리 흐름 분리 설계 전체
- `StuckSagaDetector` (비파괴 감지, 물리 흐름 전용), `DispatchTimeoutSweeper` (배차 자동 종결),
  `ChargeRetrySweeper`·`OverdueSweeper` (carry-payment 소관 — 결제 재시도·연체 확정, 주문을 건드리지 않음)
