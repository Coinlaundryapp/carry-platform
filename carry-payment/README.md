# carry-payment

> 결제 처리 및 청구서 관리

## 도메인 개요

PG사 결제 요청 및 처리, 청구서(Invoice) 생성을 담당하는 모듈이다. 결제 상태를 관리하고 `PaymentCompletedEvent`를 발행하며, `OrderCreatedEvent`를 소비하여 사가에 참여한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API
adapter/outbound/         # 외부 의존 구현 (PG사 연동)
application/port/         # 유스케이스 인터페이스
application/service/      # 비즈니스 로직
domain/model/             # 엔티티
domain/vo/                # 값 객체
domain/exception/         # 도메인 예외
```

## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v2/payments/pay` | 결제 요청 |
| GET | `/api/v2/payments/{orderId}/invoice` | 청구서 조회 |
| GET | `/api/v2/payments/{orderId}/payment` | 결제 정보 조회 |

## 주요 도메인 모델

- **Payment** — `invoiceId`, `orderId`, `customerId`, `status`, `pgProvider`, `pgTransactionId`, `amount`
- **Invoice** — `orderId`, `customerId`, `status`, `lineItems`, `weight`, `totalAmount`

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
