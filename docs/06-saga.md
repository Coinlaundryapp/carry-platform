# 06. Saga 설계 — 주문 프로세스

> 최종 수정일: 2026-06-06
> 상태: 구현 (보상 트랜잭션 포함)
> 보상 설계 상세: [superpowers/specs/2026-06-06-saga-compensation-design.md](superpowers/specs/2026-06-06-saga-compensation-design.md)

---

## Choreography Saga

중앙 오케스트레이터 없이, 각 모듈이 이벤트를 발행하고 다른 모듈이 반응하는 방식이다.

오케스트레이션 Saga 대비 선택 이유:
- 모듈 간 결합도가 낮다 (오케스트레이터가 모든 모듈을 알 필요 없음)
- 모듈을 추가/제거할 때 다른 모듈에 영향이 적다
- CDC + Outbox 패턴과 자연스럽게 결합된다

트레이드오프:
- 전체 흐름을 한눈에 파악하기 어렵다 → **Jaeger 트레이싱으로 보완**
- 이벤트 순서가 중요한 경우 주의 필요 → **aggregate_id 기반 파티셔닝으로 순서 보장**

각 모듈은 `*SagaHandler`(인바운드 포트 구현)에서 이벤트를 처리하고, `*EventConsumer`(Kafka 리스너)가 CDC-from-outbox 토픽을 구독해 핸들러로 위임한다.

---

## 정상 흐름 (Happy Path)

코인세탁 O2O 는 **결제가 픽업 이후**다. 캐리어가 세탁물을 수거해 무게를 재야 청구 금액이 확정되기 때문이다.

```
 Order              Dispatch            Delivery            Payment             Notification
   │                   │                   │                   │                    │
 주문 생성             │                   │                   │                    │
 CREATED              │                   │                   │                    │
   ├─OrderCreatedEvent→│                   │                   │                    │
   │              배차 생성 → 캐리어 수락    │                   │                    │
   │←DispatchAcceptedEvent─┤               │                   │                    │
 DISPATCHED            ├─DispatchAccepted─→│ 배달 생성           │                    │
   │                   │              수거 완료(무게)            │                    │
   │←─────PickupCompletedEvent─────────────┤                   │                    │
 PICKED_UP             │                   ├─PickupCompleted──→│ 청구서 발행          │
   │←──────────────────────InvoiceIssuedEvent──────────────────┤                    │
 INVOICED              │                   │              (고객 결제 진행)            │
   │←──────────────────────PaymentCompletedEvent───────────────┤                    │
 PAID                  │                   ├─LaundryStarted──→ (세탁)                 │
 IN_PROGRESS           │                   │              배달 완료                  │
   │←─────DeliveryCompletedEvent───────────┤                   │                    │
 COMPLETED             │                   │                   │                    │
```

실제 상태 흐름: `CREATED → DISPATCHED → PICKED_UP → INVOICED → PAID → IN_PROGRESS → COMPLETED`

---

## 보상 트랜잭션 (Compensation)

해피 패스 외의 경로. 코레오그래피이므로 각 모듈이 보상 이벤트에 독립적으로 반응한다.

### 결제 실패 → 재결제 → (시한 초과 시) 취소

결제 실패 시점엔 이미 세탁물을 픽업한 상태이므로 즉시 취소하지 않고 **재결제 창**을 준다.

```
INVOICED ──PaymentFailedEvent──→ Order: PAYMENT_FAILED   (Notification: 재결제 안내)
                                   │
                          ┌────────┴─────────┐
                   재결제 성공            24h 시한 초과
                  (PaymentCompletedEvent)   (PaymentRetryDeadlineSweeper)
                          │                     │
                          ▼                     ▼
                        PAID                 cancelOrder(SYSTEM) → CANCELLED
                                             (OrderCancelledEvent 캐스케이드,
                                              완료 결제 없으므로 환불 skip)
```

- `OrderSagaHandler.onPaymentFailed` 는 `INVOICED` 에서만 `PAYMENT_FAILED` 로 전이한다(이미 PAID/취소된 주문에 늦게 도착한 실패 이벤트는 멱등하게 무시).
- `PaymentRetryDeadlineSweeper`(@Scheduled)가 `PAYMENT_FAILED AND updated_at < now-24h` 주문을 SYSTEM 취소로 종결한다. 상태 가드로 멱등(ShedLock 분산 락은 후속 하드닝).

### 결제 완료 후 취소 → 자동 환불

`PAID` 주문을 코디네이터/시스템이 취소하면 즉시 종료가 아니라 환불 보상으로 들어간다.

```
PAID ──cancelOrder(코디/시스템)──→ Order: REFUND_PENDING
                                    │ OrderCancelledEvent
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                            ▼
 PaymentSagaHandler          DispatchSagaHandler          DeliverySagaHandler
 .onOrderCancelled           .onOrderCancelled            .onOrderCancelled
 (COMPLETED 결제 자동 환불)     (가드 통과 시 CANCELLED)      (가드 통과 시 CANCELLED)
        │ RefundCompletedEvent
        ▼
 OrderSagaHandler.onRefundCompleted → Order: REFUNDED
```

- **고객 self-cancel(`cancelOrderByCustomer`)은 PAID 환불 분기를 타지 않는다.** PAID 는 취소 가능 상태가 아니라 `cancel()` 이 거부 → 픽업 후 고객 직접 취소 차단 정책 유지. 환불 분기는 코디/시스템 전용 `cancelOrder`.
- `PaymentSagaHandler.onOrderCancelled` 는 완료된 결제가 있을 때만 `requestRefund` 를 호출하고, 없으면 조용히 skip 한다(throw 하면 DLQ 로 빠짐).
- 동일한 `OrderCancelledEvent` 로 dispatch/delivery 캐스케이드와 결제 환불을 함께 트리거한다(기존 취소 캐스케이드 재사용).

### 픽업 전 취소 (CREATED / DISPATCHED)

```
Order ──cancelOrder──→ CANCELLED
                       │ OrderCancelledEvent
                       ├─→ Dispatch: 가드 통과 시 CANCELLED
                       └─→ Payment: 완료 결제 없음 → 환불 skip
```

### 배차 실패 / 코디 취소 (기존)

```
Dispatch ──DispatchTimeoutEvent / DispatchCancelledEvent──→ Order: 취소 가능 시 CANCELLED
```

---

## 주문 상태 머신

```
 CREATED ──→ DISPATCHED ──→ PICKED_UP ──→ INVOICED ──→ PAID ──→ IN_PROGRESS ──→ COMPLETED
   │            │                            │           │
   │            │                            ▼           ▼
   ▼            ▼                       PAYMENT_FAILED  REFUND_PENDING
 CANCELLED  CANCELLED                       │  │            │
                                       재결제 │  │ 시한초과     ▼
                                       (→PAID)│  └─→ CANCELLED  REFUNDED
                                              ▼
                                            PAID
```

### 상태 전이 규칙 (코드: `carry-order/.../domain/vo/OrderEnums.kt`)

```kotlin
enum class OrderStatus {
    CREATED, DISPATCHED, PICKED_UP, INVOICED, PAYMENT_FAILED,
    PAID, IN_PROGRESS, COMPLETED, REFUND_PENDING, REFUNDED, CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED        -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED     -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP      -> target == INVOICED
        INVOICED       -> target in setOf(PAID, PAYMENT_FAILED)
        PAYMENT_FAILED -> target in setOf(PAID, CANCELLED)
        PAID           -> target in setOf(IN_PROGRESS, REFUND_PENDING)
        IN_PROGRESS    -> target == COMPLETED
        REFUND_PENDING -> target == REFUNDED
        COMPLETED      -> false
        REFUNDED       -> false
        CANCELLED      -> false
    }

    // 픽업 후(PICKED_UP/INVOICED/PAID)에는 고객 취소 불가.
    // PAYMENT_FAILED 는 스위퍼가 cancel() 로 종결할 수 있어 취소 가능.
    fun isCancellable(): Boolean = this in setOf(CREATED, DISPATCHED, PAYMENT_FAILED)
}
```

> `cancel()` 은 `isCancellable()` 만 검사하고 `_status` 를 직접 세팅(transitTo 우회). 따라서 `PAYMENT_FAILED → CANCELLED` 는 `isCancellable` 로 허용된다.

---

## Saga 이벤트 목록

| 이벤트 | 발행 모듈 | 소비(핸들러) | 트리거 |
|--------|----------|----------|--------|
| `OrderCreatedEvent` | Order | Dispatch, Notification | 주문 생성 |
| `DispatchAcceptedEvent` | Dispatch | Order, Delivery, Notification | 캐리어 수락 |
| `PickupCompletedEvent` | Delivery | Order, Payment, Notification | 수거 완료(무게 확정) |
| `InvoiceIssuedEvent` | Payment | Order, Notification | 청구서 발행 |
| `PaymentCompletedEvent` | Payment | Order, Notification | 결제 완료 |
| `PaymentFailedEvent` | Payment | Order, Notification | 결제 실패 |
| `LaundryStartedEvent` | Delivery | Order | 세탁 시작 |
| `DeliveryCompletedEvent` | Delivery | Order, Notification | 배달 완료 |
| `OrderCancelledEvent` | Order | Payment, Dispatch, Delivery | 주문 취소/환불 시작 |
| `RefundCompletedEvent` | Payment | Order | 환불 완료 |
| `DispatchTimeoutEvent` / `DispatchCancelledEvent` | Dispatch | Order, Delivery | 배차 실패/취소 |

### Kafka 토픽 구조

| 토픽 | 발행 모듈 | 파티션 키 |
|------|----------|----------|
| `carry.Order.events` | Order | aggregateId(orderId) |
| `carry.Payment.events` | Payment | aggregateId(orderId) |
| `carry.Dispatch.events` | Dispatch | aggregateId(orderId) |
| `carry.Delivery.events` | Delivery | aggregateId(deliveryId) |

`aggregateId` 를 파티션 키로 사용하면 한 애그리거트의 이벤트가 같은 파티션에 들어가 순서가 보장된다(상세: [kafka-partitioning-design](superpowers/specs/2026-06-06-kafka-partitioning-design.md)).

---

## Saga 타임아웃 / 좀비 종결

이벤트 유실·미응답·결제 미완료로 주문이 중간 상태에 멈추는 것을 방지한다.

### 재결제 시한 스위퍼 (구현됨)

```kotlin
// carry-order/.../application/service/PaymentRetryDeadlineSweeper.kt
@Scheduled(fixedRateString = "\${carry.order.payment-retry-sweep-interval-ms:3600000}")
fun sweepExpiredPaymentFailedOrders() {
    val cutoff = Instant.now().minus(deadlineHours, ChronoUnit.HOURS)  // 기본 24h
    orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.PAYMENT_FAILED, cutoff)
        .forEach { orderCommandUseCase.cancelOrder(it.id!!, "재결제 시한 초과", "SYSTEM") }
}
```

### 타임아웃 기준

| 상태 | 대기 한도 | 타임아웃 시 행동 | 상태 |
|------|----------|----------------|------|
| PAYMENT_FAILED | 24시간 | 자동 주문 취소(SYSTEM) | ✅ 구현 |
| 배차 대기(PENDING) | 정책값 | DispatchTimeout → 주문 취소 | ✅ 기존 |

---

## 검증

- 도메인 단위: `OrderStatusTest`, `OrderTest`(신규 전이/가드).
- 핸들러 단위: `OrderSagaHandlerTest`(onPaymentFailed/onRefundCompleted), `PaymentSagaHandlerTest`(onOrderCancelled), `PaymentRetryDeadlineSweeperTest`, `NotificationSagaHandlerTest`.
- 통합(Testcontainers, `carry-app/src/test/.../saga/PaymentSagaIntegrationTest`): 결제 실패→PAYMENT_FAILED / 재결제→PAID / 시한 초과 스위퍼→CANCELLED / PAID 취소→환불→REFUNDED + dispatch·delivery 캐스케이드.
