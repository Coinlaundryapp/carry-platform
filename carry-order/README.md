# carry-order

> 주문 생성, 상태 관리 및 사가 참여

## 도메인 개요

세탁물 주문 생성과 취소, 주문 상태 머신 관리를 담당하는 모듈이다. Choreography Saga의 시작점으로서 `OrderCreatedEvent`, `OrderCancelledEvent` 등의 이벤트를 발행한다. 주문 상태는 **물리 세계의 사실만** 기술한다(`CREATED`에서 `COMPLETED`까지) — 결제 생애주기(청구서 발행·자동과금·연체·환불)는 `carry-payment` 모듈이 전담하며, 물리 흐름은 결제 결과를 기다리지 않는다. 대신 주문 생성 시점에 지불수단 확보(활성 빌링키)·연체 없음을 전제조건으로 검증한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API
adapter/outbound/         # 외부 의존 구현
application/port/         # 유스케이스 인터페이스
application/service/      # 비즈니스 로직
domain/model/             # 엔티티
domain/vo/                # 값 객체
domain/exception/         # 도메인 예외
```

## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v2/orders` | 주문 생성 |
| GET | `/api/v2/orders/my` | 내 주문 목록 |
| GET | `/api/v2/orders/{orderId}` | 주문 상세 |
| POST | `/api/v2/orders/{orderId}/cancel` | 주문 취소 |

## 주요 도메인 모델

- **Order** — `customerId`, `status`, `laundromatId`, `laundryItemType`, `selectedOptions`, `shippingAddress`, `desiredPickupAt`, `desiredDeliveryAt`, `carrierId`, `actualWeight` (금액 정보는 없음 — 청구·결제 금액은 `carry-payment`의 Invoice/Payment 소관)

### 상태 머신

물리 세계의 사실만 표현한다(6개 상태). 결제 실패·연체는 이 상태 머신에 나타나지 않는다 — 상세: [docs/06-saga.md](../docs/06-saga.md).

```
CREATED → DISPATCHED → PICKED_UP → IN_PROGRESS → COMPLETED
   │           │             │            │
   ▼           ▼             ▼            ▼
CANCELLED  CANCELLED     CANCELLED    CANCELLED
(고객·코디·시스템)         (코디·시스템 전용, 수거 후 고객 self-cancel 불가)
```

- 고객 취소(`cancelOrderByCustomer`)는 수거 전(`CREATED`/`DISPATCHED`)만 허용.
- 코디네이터/시스템 취소(`cancelOrder`)는 `COMPLETED` 전이면 언제든 허용 — 수거 후 취소는 결제 모듈이 `OrderCancelledEvent`를 소비해 환불(과금 완료 건) 또는 미과금 인보이스 취소를 처리한다.
- 주문 생성 전제조건: 고객에게 활성 빌링키가 있어야 하고(`BILLING_KEY_REQUIRED`), 연체 인보이스가 없어야 한다(`OVERDUE_INVOICE_EXISTS`) — 둘 다 409로 거부.

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
