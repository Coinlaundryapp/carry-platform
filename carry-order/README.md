# carry-order

> 주문 생성, 상태 관리 및 사가 참여

## 도메인 개요

세탁물 주문 생성과 취소, 주문 상태 머신 관리를 담당하는 모듈이다. Choreography Saga의 시작점으로서 `OrderCreatedEvent`, `OrderPaidEvent` 등의 이벤트를 발행한다. 주문 상태는 `CREATED`에서 `DELIVERED`까지 단계별로 전이된다.

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

- **Order** — `customerId`, `status`, `laundromatId`, `laundryItemType`, `selectedOptions`, `shippingAddress`, `desiredPickupAt`, `desiredDeliveryAt`, `carrierId`, `totalAmount`, `actualWeight`

### 상태 머신

```
CREATED → PAYMENT_PENDING → PAID → DISPATCHED → DELIVERED
                          ↘ PAYMENT_FAILED
                                    REFUND_PENDING → REFUND_COMPLETED
                                    DISPATCH_FAILED
```

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
