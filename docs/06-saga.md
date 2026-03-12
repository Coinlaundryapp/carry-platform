# 06. Saga 설계 — 주문 프로세스

> 최종 수정일: 2026-03-11
> 상태: Draft

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

---

## 정상 흐름 (Happy Path)

```
 Order Module         Payment Module       Dispatch Module      Notification Module
      │                     │                     │                     │
 ①  주문 생성                │                     │                     │
 │  status: CREATED         │                     │                     │
 │                          │                     │                     │
 ├─ OrderCreatedEvent ─────→│                     │                     │
 │                     ②  결제 레코드 생성          │                     │
 │                     │  status: PENDING         │                     │
 │                     │  (클라이언트 결제 진행)     │                     │
 │                     │                          │                     │
 │                     ├─ PaymentCompletedEvent ──→│                     │
 │                     │                          │                     │
 │←─ PaymentCompletedEvent ─┤                     │                     │
 ③  status: PAID            │                     │                     │
 │                          │                ④  배차 생성               │
 ├─ OrderPaidEvent ────────→│────────────────→│  status: WAITING       │
 │                          │                     │                     │
 │                          │                     ├─ DispatchCreatedEvent│
 │                          │                     │                ⑤  알림│
 │                          │                     │               고객에게│
 │                          │                     │               발송   │
```

---

## 보상 트랜잭션 (Compensation)

### 결제 실패

```
Payment ── PaymentFailedEvent ──→ Order: status → PAYMENT_FAILED
                                → Notification: 결제 실패 알림
```

### 배차 실패

```
Dispatch ── DispatchFailedEvent ──→ Order: status → DISPATCH_FAILED
                                  → Payment: 환불 프로세스 시작
                                  → Notification: 배차 실패 알림
```

### 주문 취소 (고객 요청)

```
Order ── OrderCancelledEvent ──→ Payment: 환불 처리
                               → Dispatch: 배차 취소
                               → Notification: 취소 알림
```

### 환불 완료

```
Payment ── RefundCompletedEvent ──→ Order: status → REFUND_COMPLETED
                                  → Notification: 환불 완료 알림
```

---

## 주문 상태 머신

```
                    ┌──────────────────────────────────────┐
                    │                                      │
  CREATED ──→ PAYMENT_PENDING ──→ PAID ──→ DISPATCHED ──→ DELIVERED
     │              │                │          │
     │              ▼                ▼          ▼
     │        PAYMENT_FAILED   REFUND_PENDING  DISPATCH_FAILED
     │                              │
     ▼                              ▼
  CANCELLED                   REFUND_COMPLETED
```

### 상태 전이 규칙 (코드)

```kotlin
enum class OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    DISPATCHED,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED,
    DISPATCH_FAILED,
    REFUND_PENDING,
    REFUND_COMPLETED;

    fun canTransitionTo(next: OrderStatus): Boolean = when (this) {
        CREATED          -> next in setOf(PAYMENT_PENDING, CANCELLED)
        PAYMENT_PENDING  -> next in setOf(PAID, PAYMENT_FAILED, CANCELLED)
        PAID             -> next in setOf(DISPATCHED, REFUND_PENDING, CANCELLED)
        DISPATCHED       -> next in setOf(DELIVERED, DISPATCH_FAILED)
        DELIVERED        -> next == REFUND_PENDING
        CANCELLED        -> false
        PAYMENT_FAILED   -> false  // 터미널 상태
        DISPATCH_FAILED  -> next == REFUND_PENDING
        REFUND_PENDING   -> next == REFUND_COMPLETED
        REFUND_COMPLETED -> false  // 터미널 상태
    }
}
```

---

## Saga 이벤트 목록

| 이벤트 | 발행 모듈 | 소비 모듈 | 트리거 |
|--------|----------|----------|--------|
| `OrderCreatedEvent` | Order | Payment, Notification | 주문 생성 |
| `PaymentCompletedEvent` | Payment | Order, Notification | 결제 완료 |
| `PaymentFailedEvent` | Payment | Order, Notification | 결제 실패 |
| `OrderPaidEvent` | Order | Dispatch, Notification | 결제 확인 후 |
| `DispatchCreatedEvent` | Dispatch | Operation, Notification | 배차 생성 |
| `DispatchAssignedEvent` | Dispatch | Order, Notification | 라이더 배정 |
| `DispatchFailedEvent` | Dispatch | Order, Payment, Notification | 배차 실패 |
| `OrderCancelledEvent` | Order | Payment, Dispatch, Notification | 주문 취소 |
| `DeliveryCompletedEvent` | Dispatch | Order, Notification | 배송 완료 |
| `RefundCompletedEvent` | Payment | Order, Notification | 환불 완료 |

### Kafka 토픽 구조

| 토픽 | 발행 모듈 | 파티션 키 |
|------|----------|----------|
| `carry.Order.events` | Order | orderId |
| `carry.Payment.events` | Payment | orderId |
| `carry.Dispatch.events` | Dispatch | dispatchId |

`orderId`를 파티션 키로 사용하면, 하나의 주문에 관련된 모든 이벤트가 같은 파티션에 들어가 순서가 보장된다.

---

## Saga 타임아웃 처리

이벤트가 유실되거나 소비 모듈이 응답하지 않는 경우를 대비해야 한다.

### 스케줄러 기반 타임아웃 감지

```kotlin
@Scheduled(fixedDelay = 60_000)  // 1분마다 실행
fun detectStalledOrders() {
    val stalledOrders = orderRepository.findByStatusAndUpdatedAtBefore(
        status = OrderStatus.PAYMENT_PENDING,
        threshold = Instant.now().minus(Duration.ofMinutes(30))
    )
    stalledOrders.forEach { order ->
        // 30분 이상 결제 대기 → 자동 취소
        order.cancel(reason = "결제 타임아웃")
        orderRepository.save(order)
        outboxRepository.save(/* OrderCancelledEvent */)
    }
}
```

### 타임아웃 기준

| 상태 | 대기 한도 | 타임아웃 시 행동 |
|------|----------|----------------|
| PAYMENT_PENDING | 30분 | 자동 주문 취소 |
| PAID → DISPATCHED 대기 | 5분 | 운영팀 알림 |
| DISPATCHED → DELIVERED 대기 | 2시간 | 운영팀 에스컬레이션 |
