# 06. Saga 설계 — 주문 프로세스

> 최종 수정일: 2026-07-12
> 상태: 구현 (결제·물리 흐름 완전 분리)
> 보상 설계 상세: [superpowers/specs/2026-06-06-saga-compensation-design.md](superpowers/specs/2026-06-06-saga-compensation-design.md)
> 빌링키 자동과금 설계: [superpowers/specs/2026-07-12-billing-key-autocharge-design.md](superpowers/specs/2026-07-12-billing-key-autocharge-design.md)

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

**물리 흐름과 결제 흐름은 완전히 분리된 두 개의 병렬 사가다.** 결제는 더 이상 물리 흐름의 게이트가 아니다 — 배차·수거·세탁·반납 어떤 단계도 결제 결과를 기다리지 않는다. 대신 지불수단 확보(빌링키 등록)를 주문 생성 시점의 전제조건으로 앞당겨 "존재하는 주문은 결제 때문에 멈추지 않는다"는 불변식을 만든다.

### 물리 흐름 사가 (Order lane) — 결제 이벤트를 소비하지 않는다

```
 Order              Dispatch            Delivery            Notification
   │                   │                   │                    │
 주문 생성(전제조건: 빌링키 有 · 연체 無)     │                    │
 CREATED              │                   │                    │
   ├─OrderCreatedEvent→│                   │                    │
   │              배차 생성 → 캐리어 수락    │                    │
   │←DispatchAcceptedEvent─┤               │                    │
 DISPATCHED            ├─DispatchAccepted─→│ 배달 생성            │
   │                   │              수거 완료(무게 확정)         │
   │←─────PickupCompletedEvent─────────────┤                    │
 PICKED_UP             │                   ├─LaundryStarted───→ (세탁 시작 알림)
 IN_PROGRESS           │              (세탁 → 건조 → 반납)         │
   │                   │              배달 완료                   │
   │←─────DeliveryCompletedEvent───────────┤                    │
 COMPLETED             │                   │                    │
```

실제 물리 상태 흐름: `CREATED → DISPATCHED → PICKED_UP → IN_PROGRESS → COMPLETED` (결제 성공 여부와 무관하게 도달)

### 결제 흐름 사가 (Payment lane) — 물리 흐름과 병렬로, PickupCompleted 이후 결제 모듈 내부에서 독립 진행

`PickupCompletedEvent`로 인보이스가 발행되는 시점부터는 자동과금이 결제 모듈 **내부에서만** 진행된다. Order 모듈은 이 흐름의 어떤 이벤트도 구독하지 않는다.

```
 Delivery         Payment(청구서 발행)     Payment(자체 소비: 자동과금)       Notification
   │                    │                       │                          │
 수거 완료(무게)          │                       │                          │
   ├─PickupCompleted──→ 청구서 발행(ISSUED)        │                          │
   │                    ├──InvoiceIssuedEvent────→ AutoChargeService         │
   │                    │                    활성 빌링키 조회 → chargeBilling  │
   │                    │                       │                          │
   │                    │                  ┌────┴────┐                     │
   │                    │               과금 성공     과금 실패               │
   │                    │         (Invoice→PAID)  (Payment→FAILED)          │
   │                    │       PaymentCompletedEvent  PaymentFailedEvent(최초 1회만)
   │                    │                       │            └──────────→ "카드 재등록 안내"
   │                    │                       │
   │                    │              (실패 시 ChargeRetrySweeper 가 백오프로 재시도 —
   │                    │               아래 "과금 실패" 절 참조)
```

- `InvoiceIssuedEvent`는 **payment 모듈 자신이 소비**한다(자체 소비 리스너, `PaymentEventConsumer.consumePaymentEvents`) — 기존 outbox→Kafka→멱등 소비 인프라를 그대로 재사용한다.
- Notification 모듈은 `InvoiceIssuedEvent`/`PaymentCompletedEvent`/`PaymentFailedEvent`를 구독해 안내만 보낸다. 물리 상태 전이에는 관여하지 않는다.

---

## 보상 트랜잭션 (Compensation)

해피 패스 외의 경로. 코레오그래피이므로 각 모듈이 보상 이벤트에 독립적으로 반응한다. **과금 실패는 더 이상 주문을 건드리지 않는다** — 물리 흐름과 결제 흐름이 분리됐으므로, 보상은 오직 결제 모듈 내부(Payment/Invoice 상태)에서만 일어난다.

### 과금 실패 → 백오프 재시도(ChargeRetrySweeper) → 72h 연체(OverdueSweeper) → 카드 재등록 시 회복

`AutoChargeService.chargeInvoice` 가 실패해도 주문은 물리 흐름을 계속 진행한다(픽업 → 세탁 → 반납 → COMPLETED, 결제와 무관). 실패의 귀결은 오직 **신규 주문 차단**뿐이다.

```
ISSUED ──과금 실패──→ Payment: FAILED (next_retry_at 예약)   (Notification: 최초 1회만 "카드 재등록 안내")
   │
   │  ChargeRetrySweeper 가 next_retry_at 도래 건을 주기 스캔 (백오프: 1h→4h→12h→24h→이후 24h 고정)
   │  매 시도마다 그 시점의 **활성** 빌링키를 다시 조회 → 카드 재등록이 자연스러운 회복 경로
   │
   ├──재과금 성공──→ Invoice: PAID, Payment: COMPLETED  (PaymentCompletedEvent)
   │
   └──72h 경과(OverdueSweeper, 재시도 소진 여부와 무관)──→ Invoice: OVERDUE
                                                          │
                                          해당 customerId 의 신규 주문 생성이
                                          409 OVERDUE_INVOICE_EXISTS 로 차단
                                          (물리 흐름 중인 기존 주문은 영향 없음)
                                                          │
                                          이후 재과금 성공 시 OVERDUE → PAID
                                          → 차단 자연 해제
```

- `ChargeRetrySweeper`/`OverdueSweeper` 모두 `@Scheduled` + `@SchedulerLock`(ShedLock) 스위퍼로, 상태 가드(`markOverdueIfIssued` 조건부 UPDATE 등)로 멱등·레이스 안전을 확보한다.
- `PaymentFailedEvent`는 알림 스팸을 막기 위해 **최초 실패 시 1회만** 발행한다 — 스위퍼 재시도 실패는 이벤트 없이 `next_retry_at`만 갱신한다.
- Order 모듈은 이 흐름의 어떤 이벤트도 구독하지 않는다(`OrderSagaHandler`에 결제 이벤트 핸들러 없음). 과금 실패·연체가 물리 흐름을 취소시키지 않는다.

### 수거 후 취소 (코디네이터/시스템 전용) → 환불 또는 미과금 인보이스 취소

`PICKED_UP`/`IN_PROGRESS` 주문을 코디네이터/시스템이 취소하면 주문은 **즉시 `CANCELLED`로 종결**한다(주문 상태에 환불 대기 상태는 없다 — 환불 진행 상태는 결제 모듈 소관).

```
Order(PICKED_UP/IN_PROGRESS) ──cancelOrder(코디/시스템)──→ CANCELLED
                                    │ OrderCancelledEvent
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                            ▼
 PaymentSagaHandler          DispatchSagaHandler          DeliverySagaHandler
 .onOrderCancelled           .onOrderCancelled            .onOrderCancelled
        │                    (가드 통과 시 CANCELLED)      (가드 통과 시 CANCELLED)
        │
   Payment COMPLETED?
   ├─Yes → 환불 대기 마킹 → RefundRetrySweeper 가 PG 환불 (기존 유지) → RefundCompletedEvent
   │                                                                  (Notification 만 소비, 주문 상태 전이 없음)
   └─No → 인보이스 ISSUED/OVERDUE 면 CANCELLED 로 전환, FAILED Payment 는 재시도 대상에서 자연 배제
```

- **고객 self-cancel(`cancelOrderByCustomer`)은 이 분기를 타지 않는다.** `isCancellableBy(CUSTOMER)` 는 `CREATED`/`DISPATCHED`만 허용 — 픽업 후 고객 직접 취소 차단 정책 유지. 수거 후 취소는 코디/시스템 전용 `cancelOrder`.
- `PaymentSagaHandler.onOrderCancelled` 는 완료된 결제가 있을 때만 환불 대기로 표시하고, 없으면(미과금 인보이스만 있거나 아예 없으면) 조용히 skip/취소 처리한다(throw 하면 DLQ 로 빠짐).
- 동일한 `OrderCancelledEvent` 로 dispatch/delivery 캐스케이드와 결제 보상을 함께 트리거한다(기존 취소 캐스케이드 재사용).

### 픽업 전 취소 (CREATED / DISPATCHED, 고객·코디·시스템 공통)

```
Order ──cancelOrder──→ CANCELLED
                       │ OrderCancelledEvent
                       ├─→ Dispatch: 가드 통과 시 CANCELLED
                       └─→ Payment: 인보이스 없음 → skip
```

### 배차 실패 / 코디 취소 (기존)

```
Dispatch ──DispatchTimeoutEvent / DispatchCancelledEvent──→ Order: 취소 가능 시 CANCELLED
```

---

## 주문 상태 머신

물리 세계의 사실만 기술한다(11개 → 6개로 축소). 결제 생애주기(청구서·과금·연체·환불)는 `carry-payment`(Invoice/Payment)가 전담하며 `OrderStatus`에 노출되지 않는다.

```
 CREATED ──→ DISPATCHED ──→ PICKED_UP ──→ IN_PROGRESS ──→ COMPLETED
   │            │                │             │
   ▼            ▼                ▼             ▼
 CANCELLED  CANCELLED       CANCELLED     CANCELLED
 (고객·코디·시스템)  (고객·코디·시스템)   (코디·시스템 전용)  (코디·시스템 전용)
```

### 상태 전이 규칙 (코드: `carry-order/.../domain/vo/OrderEnums.kt`)

```kotlin
enum class OrderStatus {
    CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS, COMPLETED, CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP -> target in setOf(IN_PROGRESS, CANCELLED)
        IN_PROGRESS -> target in setOf(COMPLETED, CANCELLED)
        COMPLETED -> false
        CANCELLED -> false
    }

    // 취소 가능 여부는 행위자에 따라 다르다 — 고객은 수거 전만, 코디/시스템은 완료 전까지.
    fun isCancellableBy(by: CancelledBy): Boolean = when (by) {
        CancelledBy.CUSTOMER -> this in setOf(CREATED, DISPATCHED)
        CancelledBy.COORDINATOR, CancelledBy.SYSTEM ->
            this in setOf(CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS)
    }

    // forward 사가 진행 중 여부 — 늦게 도착한 이벤트의 멱등 no-op 판정 술어.
    fun isForwardActive(): Boolean = this !in setOf(COMPLETED, CANCELLED)
}
```

> 삭제된 상태: `INVOICED`, `PAID`, `PAYMENT_FAILED`, `REFUND_PENDING`, `REFUNDED`. `IN_PROGRESS` 전이는 결제 이벤트가 아니라 물리 행동(수거 완료 이후 세탁 시작, `LaundryStartedEvent`)이 트리거한다.

---

## Saga 이벤트 목록

| 이벤트 | 발행 모듈 | 소비(핸들러) | 트리거 |
|--------|----------|----------|--------|
| `OrderCreatedEvent` | Order | Dispatch, Notification | 주문 생성 |
| `DispatchAcceptedEvent` | Dispatch | Order, Delivery, Notification | 캐리어 수락 |
| `PickupCompletedEvent` | Delivery | Order, **Payment**, Notification | 수거 완료(무게 확정) — Order 는 물리 전이(PICKED_UP), Payment 는 인보이스 발행 |
| `InvoiceIssuedEvent` | Payment | **Payment(자체 소비)**, Notification | 청구서 발행 → 자동과금 시작. **Order 는 구독하지 않음** |
| `PaymentCompletedEvent` | Payment | Notification | 자동과금 완료. **Order 는 구독하지 않음** |
| `PaymentFailedEvent` | Payment | Notification | 자동과금 실패(최초 1회만 발행). **Order 는 구독하지 않음** |
| `LaundryStartedEvent` | Delivery | Order | 세탁 시작 (물리 전이: IN_PROGRESS) |
| `DeliveryCompletedEvent` | Delivery | Order, Notification | 배달 완료 |
| `OrderCancelledEvent` | Order | Payment, Dispatch, Delivery | 주문 취소(환불/미과금 인보이스 취소는 Payment 내부 처리) |
| `RefundCompletedEvent` | Payment | Notification | 환불 완료. **Order 는 구독하지 않음**(주문은 이미 CANCELLED) |
| `DispatchTimeoutEvent` / `DispatchCancelledEvent` | Dispatch | Order, Delivery | 배차 실패/취소 |

> `OrderSagaHandler`는 결제 이벤트 핸들러를 전혀 갖지 않는다(onInvoiceIssued/onPaymentCompleted/onPaymentFailed/onRefundCompleted 모두 제거됨) — `OrderEventConsumer`가 구독하는 토픽은 `carry.Dispatch.events`/`carry.Delivery.events`뿐이다.

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

이벤트 유실·미응답으로 주문이 중간 상태에 멈추는 것을 방지한다. **결제(과금) 미완료는 더 이상 주문을 종결하지 않는다** — 그 관심사는 아래 두 결제 스위퍼가 전담한다.

### 결제 스위퍼 — ChargeRetrySweeper / OverdueSweeper (구현됨, carry-payment 소관)

```kotlin
// carry-payment/.../application/service/ChargeRetrySweeper.kt
@Scheduled(fixedRateString = "\${carry.payment.charge-retry-interval-ms:600000}")
fun retryFailedCharges() {
    paymentPersistencePort.findRetryableFailed(clock.instant())  // next_retry_at 도래 건
        .forEach { autoChargeService.retryCharge(it.id!!) }      // 백오프: 1h→4h→12h→24h→이후 24h 고정
}

// carry-payment/.../application/service/OverdueSweeper.kt
@Scheduled(fixedRateString = "\${carry.payment.overdue-sweep-interval-ms:3600000}")
fun markOverdueInvoices() {
    val cutoff = clock.instant().minus(Duration.ofHours(thresholdHours))  // 기본 72h
    invoicePersistencePort.findIssuedBefore(cutoff)
        .forEach { invoicePersistencePort.markOverdueIfIssued(it.id!!, clock.instant()) }
}
```

주문 모듈에는 이제 물리 사가만 감시하는 `StuckSagaDetector`(안전망, 비파괴)가 있을 뿐 — 결제 실패를 원인으로 주문을 자동 취소하는 스위퍼는 없다(구 `PaymentRetryDeadlineSweeper`는 제거).

### 타임아웃 기준

| 대상 | 대기 한도 | 타임아웃 시 행동 | 상태 |
|------|----------|----------------|------|
| FAILED Payment 재과금 | 1h→4h→12h→24h→이후 24h(백오프) | ChargeRetrySweeper 가 재과금 | ✅ 구현 |
| ISSUED Invoice 미결제 | 72시간 | OverdueSweeper 가 OVERDUE 마킹 → 신규 주문만 차단 | ✅ 구현 |
| 물리 사가 비종결 정체(CREATED/DISPATCHED/PICKED_UP/IN_PROGRESS) | 24시간 | `StuckSagaDetector` 가 메트릭·경고로 가시화(비파괴, 자동 취소 없음) | ✅ 구현 |
| 배차 대기(PENDING) | `desiredPickupAt` 30분 전 — 현재 설정값이 아니라 상수이며 두 곳에 중복(`carry-dispatch/.../domain/model/Dispatch.kt` `isExpired`: `minus(30, MINUTES)`, `DispatchJpaRepository.kt` `findExpiredPendingDispatches`: `INTERVAL '30 minutes'`; 프로퍼티로 뺀 것은 스윕 주기 `carry.dispatch.timeout-sweep-interval-ms` 뿐, [15-invariant-catalog §6.1](15-invariant-catalog.md)) | DispatchTimeout → 주문 취소 | ✅ 기존 |

---

## 검증

- 도메인 단위: `OrderStatusTest`, `OrderTest`(축소된 전이/가드), `BillingKeyTest`, `BillingKeyCryptoConverterTest`.
- 핸들러/서비스 단위: `OrderSagaHandlerTest`, `PaymentSagaHandlerTest`(onOrderCancelled), `AutoChargeServiceTest`, `ChargeRetrySweeperTest`, `OverdueSweeperTest`, `BillingKeyServiceTest`, `NotificationSagaHandlerTest`.
- 통합(Testcontainers, `carry-app/src/test/.../saga/AutoChargeSagaIntegrationTest`): 결제 이벤트 소비 없이 물리 흐름이 COMPLETED 도달 / 과금 실패에도 주문 진행 / 72h 경과→OVERDUE→신규 주문 409 / 카드 재등록 후 재과금 성공→PAID / 수거 후 취소 시 환불 또는 미과금 인보이스 취소 + dispatch·delivery 캐스케이드.
