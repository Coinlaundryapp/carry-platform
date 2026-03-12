# Phase 3 Overview — Order Saga

## 기존 설계 문서(06-saga.md)와의 차이

기존 문서의 플로우: `Order → Payment → Dispatch` (선결제 후 배차)
실제 비즈니스 플로우: `Order → Dispatch → Pickup(계량) → Invoice → Payment → Delivery`

가장 큰 차이는 **결제 시점**이다. 우리 서비스는 주문 시점에 결제하지 않는다.
캐리어가 세탁물을 수거하고 무게를 측정한 후에야 정확한 청구서가 발행된다.

---

## 모듈 구성 (4개)

| 모듈 | 책임 | 핵심 액터 | DB 테이블 접두사 |
|------|------|-----------|------------------|
| **carry-order** | 주문 생성, 상태 추적, 취소, 사가의 중심축 | 고객 | `order_` |
| **carry-payment** | 청구서 발행, PG 결제, 환불 | 고객, 코디네이터 | `payment_` |
| **carry-dispatch** | 배차 (일감 배정/수락/거부), 캐리어 구역, 페널티 | 코디네이터, 캐리어 | `dispatch_` |
| **carry-delivery** | 배달 워크플로우 (수거→계량→세탁→건조→배달), 단계별 사진 | 캐리어 | `delivery_` |

---

## 프론트엔드 ↔ 모듈 매핑

```
[고객 앱]
  ├── carry-order: 주문 생성, 주문 상태 조회, 주문 취소
  └── carry-payment: 청구서 조회, 결제 요청

[캐리어 앱]
  ├── carry-dispatch: 가용 일감 조회, 일감 수락/거부
  └── carry-delivery: 수거/계량/세탁/건조/배달 단계 진행, 사진 업로드

[코디네이터 운영 웹]
  ├── carry-order: 전체 주문 조회/관리
  ├── carry-dispatch: 캐리어 배정, 구역 관리, 배차 취소
  └── carry-payment: 환불 처리
```

---

## Happy Path 전체 플로우

```
1. 고객 → 주문 생성
   ┌─────────────────────────────────────────────┐
   │ carry-order                                  │
   │ Order 생성 (status: CREATED)                 │
   │ → Outbox: OrderCreatedEvent                  │
   └─────────────┬───────────────────────────────┘
                 ▼
2. 배차 생성
   ┌─────────────────────────────────────────────┐
   │ carry-dispatch                               │
   │ Dispatch 생성 (status: PENDING)              │
   │ 해당 구역 캐리어들에게 알림                      │
   └─────────────┬───────────────────────────────┘
                 ▼
3. 캐리어 배정
   ┌─────────────────────────────────────────────┐
   │ carry-dispatch                               │
   │ 캐리어 직접 수락 or 코디네이터 강제 배정           │
   │ Dispatch (status: ACCEPTED)                  │
   │ → Outbox: DispatchAcceptedEvent              │
   └──┬──────────┬──────────────────────────────┘
      │          ▼
      │  ┌────────────────────────────────────────┐
      │  │ carry-order                             │
      │  │ Order (status: DISPATCHED)              │
      │  └────────────────────────────────────────┘
      ▼
4. 수거 & 계량
   ┌─────────────────────────────────────────────┐
   │ carry-delivery                               │
   │ Delivery 생성 (status: PICKUP_PENDING)       │
   │ 캐리어가 고객 집 방문 → 수거 → 무게 측정         │
   │ Delivery (status: PICKED_UP)                 │
   │ → Outbox: PickupCompletedEvent (weight 포함)  │
   └──┬──────────┬──────────────────────────────┘
      │          ▼
      │  ┌────────────────────────────────────────┐
      │  │ carry-order                             │
      │  │ Order (status: PICKED_UP, weight 기록)   │
      │  └────────────────────────────────────────┘
      ▼
5. 청구서 발행
   ┌─────────────────────────────────────────────┐
   │ carry-payment                                │
   │ weight + 선택 옵션으로 가격 계산 (carry-price)   │
   │ Invoice 생성 (status: ISSUED)                │
   │ → Outbox: InvoiceIssuedEvent                 │
   └──┬──────────────────────────────────────────┘
      ▼
   ┌─────────────────────────────────────────────┐
   │ carry-order                                  │
   │ Order (status: INVOICED, totalAmount 기록)    │
   └─────────────────────────────────────────────┘

6. 결제 (고객이 앱에서 결제)
   ┌─────────────────────────────────────────────┐
   │ carry-payment                                │
   │ PG 결제 처리 (Toss Payments)                  │
   │ Payment (status: COMPLETED)                  │
   │ → Outbox: PaymentCompletedEvent              │
   └──┬──────────────────────────────────────────┘
      ▼
   ┌─────────────────────────────────────────────┐
   │ carry-order                                  │
   │ Order (status: PAID)                         │
   └─────────────────────────────────────────────┘

7. 세탁/건조 (캐리어가 병행 수행)
   ┌─────────────────────────────────────────────┐
   │ carry-delivery                               │
   │ Delivery (status: IN_LAUNDRY)                │
   │ 세탁 → 건조 (단계별 사진 업로드)                 │
   │ Delivery (status: LAUNDRY_COMPLETE)          │
   └─────────────────────────────────────────────┘

8. 배달 (결제 완료 확인 후)
   ┌─────────────────────────────────────────────┐
   │ carry-delivery                               │
   │ 결제 완료 여부 확인 (carry-payment sync port)   │
   │ 배달 완료 → 사진 업로드                         │
   │ Delivery (status: DELIVERED)                 │
   │ → Outbox: DeliveryCompletedEvent             │
   └──┬──────────────────────────────────────────┘
      ▼
   ┌─────────────────────────────────────────────┐
   │ carry-order                                  │
   │ Order (status: COMPLETED)                    │
   └─────────────────────────────────────────────┘
```

---

## 취소/보상 시나리오

### A. 배차 타임아웃 자동 취소
- 조건: 수거 요청 시각 30분 전까지 캐리어 미배정
- carry-dispatch 스케줄러가 감지 → Dispatch(TIMEOUT)
- → `DispatchTimeoutEvent` → carry-order → Order(CANCELLED)

### B. 코디네이터 배차 취소
- 코디네이터가 수동으로 배차 취소
- Dispatch(CANCELLED) → `DispatchCancelledEvent`
- → carry-order → Order(CANCELLED)
- → carry-delivery → Delivery(CANCELLED) (이미 생성된 경우)

### C. 고객 주문 취소 (수거 완료 전)
- 고객이 앱에서 주문 취소
- Order(CANCELLED) → `OrderCancelledEvent`
- → carry-dispatch → Dispatch(CANCELLED)
- → carry-delivery → Delivery(CANCELLED) (이미 생성된 경우)

### D. 캐리어 강제 배정 거부
- 코디네이터가 배정 → 캐리어가 거부
- Dispatch에 PenaltyRecord 기록
- Dispatch는 PENDING으로 복귀 (재배정 대기)

### E. 결제 후 환불
- 코디네이터가 환불 처리 (운영 웹)
- Payment(REFUNDED) → `RefundCompletedEvent`
- → carry-order → Order(REFUNDED)

---

## 취소 가능 시점 정리

| 주문 상태 | 고객 취소 | 코디네이터 취소 | 자동 취소 |
|-----------|----------|----------------|----------|
| CREATED | ✅ | ✅ | ✅ (타임아웃) |
| DISPATCHED | ✅ | ✅ | ❌ |
| PICKED_UP 이후 | ❌ | ❌ (환불만 가능) | ❌ |

> 수거 완료(PICKED_UP) 이후에는 취소 불가. 세탁물이 이미 캐리어에게 있기 때문.

---

## 모듈 간 통신 정리

### 비동기 이벤트 (Outbox → Debezium → Kafka)

| 이벤트 | 발행 모듈 | 소비 모듈 | 트리거 |
|--------|----------|----------|--------|
| `OrderCreatedEvent` | order | dispatch | 주문 생성 |
| `OrderCancelledEvent` | order | dispatch, delivery | 고객 취소 |
| `DispatchAcceptedEvent` | dispatch | order, delivery | 캐리어 배정 완료 |
| `DispatchTimeoutEvent` | dispatch | order | 배차 타임아웃 |
| `DispatchCancelledEvent` | dispatch | order, delivery | 코디네이터 취소 |
| `PickupCompletedEvent` | delivery | order, payment | 수거 & 계량 완료 |
| `InvoiceIssuedEvent` | payment | order | 청구서 발행 |
| `PaymentCompletedEvent` | payment | order | 결제 완료 |
| `PaymentFailedEvent` | payment | order | 결제 실패 |
| `RefundCompletedEvent` | payment | order | 환불 완료 |
| `DeliveryCompletedEvent` | delivery | order | 배달 완료 |

### 동기 조회 (Outbound Port → 직접 호출)

| 호출 모듈 | 대상 모듈 | 용도 |
|----------|----------|------|
| carry-order | carry-user | 배송지 정보 조회 |
| carry-order | carry-laundromat | 세탁소 정보 조회 |
| carry-payment | carry-price | 가격 계산 |
| carry-delivery | carry-payment | 결제 완료 여부 확인 |

---

## Kafka 토픽 구조

```
carry.Order.events       (partition key: orderId)
carry.Payment.events     (partition key: orderId)
carry.Dispatch.events    (partition key: orderId)
carry.Delivery.events    (partition key: orderId)
```

모든 토픽이 orderId를 파티션 키로 사용하여, 하나의 주문에 관련된 이벤트의 순서를 보장한다.

---

## 계정 타입 변경 (carry-user)

| 기존 | 변경 | 설명 |
|------|------|------|
| CUSTOMER | CUSTOMER | 유지 |
| RIDER | CARRIER | 배달부 → 캐리어 |
| OWNER | (삭제) | 용도 불명, 제거 |
| ADMIN | ADMIN | 시스템 관리자, 유지 |
| - | COORDINATOR | 서비스 운영자 (신규) |

---

## settings.gradle.kts 변경

`carry-delivery` 모듈 추가 필요:
```kotlin
include("carry-delivery")
```
