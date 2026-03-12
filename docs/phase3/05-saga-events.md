# Saga 이벤트 흐름 상세

## carry-event 모듈 이벤트 재정의

기존 carry-event의 이벤트 정의는 "선결제 후 배차" 플로우 기반이었다.
실제 비즈니스 플로우에 맞게 재정의한다.

---

## 이벤트 목록

### Order Events

```kotlin
package com.carry.event.order

// 주문 생성 (Order → Dispatch)
data class OrderCreatedEvent(
    val orderId: Long,
    val customerId: Long,
    val laundromatId: Long,
    val laundryItemType: String,           // LaundryItemType.name
    val selectedOptions: List<SelectedOptionDto>,
    val shippingAddress: ShippingAddressDto,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    val areaCode: String,                  // 배송지 행정구역 코드 (배차용)
)

data class SelectedOptionDto(
    val optionType: String,
    val subOptionType: String,
)

data class ShippingAddressDto(
    val roadAddress: String,
    val detailAddress: String,
    val latitude: Double,
    val longitude: Double,
    val recipientName: String,
    val recipientPhone: String,
)

// 주문 취소 (Order → Dispatch, Delivery)
data class OrderCancelledEvent(
    val orderId: Long,
    val reason: String,
    val cancelledBy: String,  // CUSTOMER, COORDINATOR, SYSTEM
)
```

### Dispatch Events

```kotlin
package com.carry.event.dispatch

// 배차 수락 완료 (Dispatch → Order, Delivery)
data class DispatchAcceptedEvent(
    val dispatchId: Long,
    val orderId: Long,
    val carrierId: Long,
    val laundromatId: Long,
)

// 배차 타임아웃 (Dispatch → Order)
data class DispatchTimeoutEvent(
    val dispatchId: Long,
    val orderId: Long,
)

// 배차 취소 (Dispatch → Order, Delivery)
data class DispatchCancelledEvent(
    val dispatchId: Long,
    val orderId: Long,
    val reason: String,
)
```

### Delivery Events

```kotlin
package com.carry.event.delivery

// 수거 완료 (Delivery → Order, Payment)
data class PickupCompletedEvent(
    val deliveryId: Long,
    val orderId: Long,
    val carrierId: Long,
    val customerId: Long,
    val actualWeight: BigDecimal,
    val laundryItemType: String,
    val orderUnitType: String,
    val orderRequestType: String,
    val selectedOptions: List<SelectedOptionDto>,
)

// 세탁 시작 (Delivery → Order) — 상태 업데이트용
data class LaundryStartedEvent(
    val deliveryId: Long,
    val orderId: Long,
)

// 배달 완료 (Delivery → Order)
data class DeliveryCompletedEvent(
    val deliveryId: Long,
    val orderId: Long,
    val carrierId: Long,
)
```

### Payment Events

```kotlin
package com.carry.event.payment

// 청구서 발행 (Payment → Order)
data class InvoiceIssuedEvent(
    val invoiceId: Long,
    val orderId: Long,
    val totalAmount: Long,
    val lineItems: List<InvoiceLineItemDto>,
)

data class InvoiceLineItemDto(
    val chargeType: String,
    val description: String,
    val amount: Long,
)

// 결제 완료 (Payment → Order)
data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val invoiceId: Long,
    val amount: Long,
)

// 결제 실패 (Payment → Order)
data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String,
)

// 환불 완료 (Payment → Order)
data class RefundCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val refundAmount: Long,
)
```

---

## Saga 시퀀스 다이어그램

### Happy Path

```
Customer        Order           Dispatch         Delivery         Payment
   │               │                │                │                │
   │─ createOrder ─►│                │                │                │
   │               │── OrderCreatedEvent ──────►│                │                │
   │               │                │                │                │
   │               │                │─ create ──►│  (PENDING)     │                │
   │               │                │                │                │
   │               │                │◄── carrier claims ──────────│                │
   │               │                │                │                │
   │               │◄── DispatchAcceptedEvent ──│                │                │
   │               │  (DISPATCHED)  │                │                │
   │               │                │── DispatchAcceptedEvent ──►│                │
   │               │                │                │─ create ──►│  (PICKUP_PENDING)
   │               │                │                │                │
   │               │                │                │◄── carrier completes pickup ─│
   │               │                │                │  (PICKED_UP)   │
   │               │                │                │                │
   │               │◄── PickupCompletedEvent ──────────────────│                │
   │               │  (PICKED_UP)   │                │                │
   │               │                │                │── PickupCompletedEvent ──►│
   │               │                │                │                │─ create invoice
   │               │                │                │                │  (ISSUED)
   │               │◄── InvoiceIssuedEvent ────────────────────────────────────│
   │               │  (INVOICED)    │                │                │
   │◄── 청구서 ─────│                │                │                │
   │               │                │                │                │
   │               │                │                │◄── washing ──│  (IN_LAUNDRY)
   │               │◄── LaundryStartedEvent ──────────────────│                │
   │               │  (IN_PROGRESS) │                │                │
   │               │                │                │                │
   │─ 결제 ────────►│                │                │── 결제 요청 ──►│
   │               │                │                │                │─ PG 승인
   │               │◄── PaymentCompletedEvent ─────────────────────────────────│
   │               │  (PAID)        │                │                │
   │               │                │                │                │
   │               │                │                │◄── drying done │
   │               │                │                │  (LAUNDRY_COMPLETE)
   │               │                │                │                │
   │               │                │                │◄── delivery ──│
   │               │                │                │  check: paid? ──►│ ✅
   │               │                │                │  (DELIVERED)   │
   │               │◄── DeliveryCompletedEvent ────────────────│                │
   │               │  (COMPLETED)   │                │                │
```

### 배차 타임아웃

```
Customer        Order           Dispatch         Scheduler
   │               │                │                │
   │─ createOrder ─►│                │                │
   │               │── OrderCreatedEvent ──────►│                │
   │               │                │  (PENDING)     │
   │               │                │                │
   │               │                │       (30분 전 도달, 미배차)
   │               │                │                │
   │               │                │◄── checkExpired ──────────│
   │               │                │  (TIMEOUT)     │
   │               │◄── DispatchTimeoutEvent ──│                │
   │               │  (CANCELLED)   │                │
   │◄── 취소 알림 ──│                │                │
```

### 고객 취소 (배차 후, 수거 전)

```
Customer        Order           Dispatch         Delivery
   │               │                │                │
   │  (DISPATCHED)  │  (ACCEPTED)    │  (PICKUP_PENDING)
   │               │                │                │
   │─ cancelOrder ─►│                │                │
   │               │  (CANCELLED)   │                │
   │               │── OrderCancelledEvent ──►│                │
   │               │                │  (CANCELLED)   │
   │               │── OrderCancelledEvent ─────────►│
   │               │                │                │  (CANCELLED)
```

---

## Kafka 토픽 & 컨슈머 그룹

### 토픽

| 토픽 | 발행 모듈 | 파티션 키 |
|------|----------|----------|
| `carry.Order.events` | carry-order | orderId |
| `carry.Dispatch.events` | carry-dispatch | orderId |
| `carry.Delivery.events` | carry-delivery | orderId |
| `carry.Payment.events` | carry-payment | orderId |

### 컨슈머 그룹

| 그룹 | 모듈 | 구독 토픽 | 처리 이벤트 |
|------|------|----------|------------|
| `order-group` | carry-order | Dispatch, Delivery, Payment | DispatchAccepted, DispatchTimeout, DispatchCancelled, PickupCompleted, LaundryStarted, InvoiceIssued, PaymentCompleted, PaymentFailed, DeliveryCompleted, RefundCompleted |
| `dispatch-group` | carry-dispatch | Order | OrderCreated, OrderCancelled |
| `delivery-group` | carry-delivery | Dispatch, Order | DispatchAccepted, OrderCancelled, DispatchCancelled |
| `payment-group` | carry-payment | Delivery | PickupCompleted |

---

## 이벤트 처리 시 Order 상태 전이 요약

| 수신 이벤트 | Order 상태 전이 | 추가 동작 |
|------------|----------------|----------|
| `DispatchAcceptedEvent` | CREATED → DISPATCHED | carrierId 기록 |
| `DispatchTimeoutEvent` | CREATED → CANCELLED | cancelReason = "배차 시간 초과" |
| `DispatchCancelledEvent` | CREATED/DISPATCHED → CANCELLED | cancelReason 기록 |
| `PickupCompletedEvent` | DISPATCHED → PICKED_UP | actualWeight 기록 |
| `InvoiceIssuedEvent` | PICKED_UP → INVOICED | invoiceId, totalAmount 기록 |
| `PaymentCompletedEvent` | INVOICED → PAID | — |
| `PaymentFailedEvent` | (상태 유지) | 고객에게 재결제 요청 알림 |
| `LaundryStartedEvent` | PAID → IN_PROGRESS | — |
| `DeliveryCompletedEvent` | IN_PROGRESS → COMPLETED | completedAt 기록 |
| `RefundCompletedEvent` | PAID → REFUNDED | — |

---

## 멱등성 보장

모든 이벤트 컨슈머는 `ProcessedEvent` 테이블을 사용하여 중복 처리를 방지한다.

```kotlin
@Transactional
fun handleEvent(eventId: String, block: () -> Unit) {
    if (processedEventRepository.existsById(eventId)) return
    block()
    processedEventRepository.save(ProcessedEvent(eventId, Instant.now()))
}
```

carry-infra-kafka에 이미 `ProcessedEvent` 엔티티와 레포지토리가 준비되어 있다.

---

## 트레이스 전파

1. 주문 생성 API 호출 시 OTel이 자동으로 traceId 생성
2. Outbox 이벤트에 traceId 포함
3. Debezium → Kafka 헤더에 traceId 전파
4. 컨슈머가 traceId로 span 복원

결과: Jaeger에서 하나의 traceId로 Order → Dispatch → Delivery → Payment 전체 흐름을 추적할 수 있다.

---

## 구현 순서 제안

모듈 간 의존성을 고려한 구현 순서:

```
1. carry-event 이벤트 재정의 (모든 모듈의 기반)
2. carry-order (사가의 중심, 다른 모듈 이벤트 수신 핸들러는 스텁)
3. carry-dispatch (OrderCreatedEvent 수신, DispatchAcceptedEvent 발행)
4. carry-delivery (DispatchAcceptedEvent 수신, PickupCompletedEvent 발행)
5. carry-payment (PickupCompletedEvent 수신, InvoiceIssued/PaymentCompleted 발행)
6. UserRole 변경 (RIDER→CARRIER, OWNER 삭제, COORDINATOR 추가)
7. settings.gradle.kts에 carry-delivery 추가
8. 통합 테스트 (Testcontainers로 전체 사가 플로우)
```
