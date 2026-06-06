# Saga 보상 트랜잭션 설계

> 작성일: 2026-06-06
> 상태: 구현
> 관련 갭: 아키텍처 감사 "최대 갭 ④ — Saga 보상 없음"
> 기존 문서: [06-saga.md](../../06-saga.md)

---

## 1. 배경 — 무엇이 비어 있었나

코레오그래피 saga는 **해피 패스만** 동작했다. 실측한 빈 곳:

- `OrderSagaHandler.onPaymentFailed` 는 로그만 남기고 주문을 `INVOICED` 에 방치 → **좀비 주문**.
- `OrderSagaHandler.onRefundCompleted` 는 로그만 — `REFUNDED` 상태 자체가 없었다.
- `PaymentCommandService.requestRefund()` 는 존재하지만 **어떤 이벤트도 자동 호출하지 않는 수동 전용** 코드 → 자동 환불 없음.
- 실패/보상 경로 통합테스트 **0건**.
- 재결제 시한이 지난 주문을 종결할 **스위퍼 없음**.

기존 부분 보상은 `onDispatchTimeout`/`onDispatchCancelled` → 주문 취소 캐스케이드(`OrderCancelledEvent` → dispatch/delivery 취소)뿐이었다.

## 2. 제품 결정 (잠금)

1. **결제 실패 의미론 = 재결제 우선**. 코인세탁은 결제가 픽업 이후라, 결제 실패 시점에 이미 세탁물을 픽업한 상태다. 즉시 취소하지 않고 재결제 창을 준다.
2. **보상 범위 = 결제 실패 경로 + 환불 루프 완성**. 포스트결제 운영 실패(세탁물 파손/배송 불가)는 신규 이벤트가 필요하므로 스코프 제외.
3. **코레오그래피 유지** — 중앙 오케스트레이터 도입 안 함(코드베이스·06-saga.md 일관).

## 3. 설계

### A. Order 상태머신 확장

신규 상태: `PAYMENT_FAILED`(재시도 가능), `REFUND_PENDING`, `REFUNDED`(종료).

전이 추가:
- `INVOICED → PAYMENT_FAILED` (결제 실패)
- `PAYMENT_FAILED → PAID` (재결제 성공)
- `PAYMENT_FAILED → CANCELLED` (재결제 시한 초과 — `cancel()` 경로)
- `PAID → REFUND_PENDING` (결제 후 취소 = 환불 보상 시작)
- `REFUND_PENDING → REFUNDED` (환불 완료)

`isCancellable()` 에 `PAYMENT_FAILED` 추가(스위퍼가 `cancel()` 로 종결). `cancel()` 은 `isCancellable` 만 검사하고 `_status` 를 직접 세팅(transitTo 우회)하므로 isCancellable 추가만으로 충분하다.

도메인 메서드: `markPaymentFailed()`, `markRefundPending()`, `markRefunded()`. `markPaid()` 는 `transitTo(PAID)` 를 쓰므로 `PAYMENT_FAILED → PAID` 전이 추가만으로 재결제를 자동 허용한다.

### B. 죽은 핸들러 연결 (`OrderSagaHandler`)

- `onPaymentFailed` → `order.markPaymentFailed()`. `status == INVOICED` 가드(멱등/순서 안전 — 이미 PAID/취소된 주문에 늦게 도착한 실패 이벤트 무시).
- `onRefundCompleted` → `order.markRefunded()`. `REFUND_PENDING` 에서만.

### C. 환불 루프 자동화

- `OrderCommandService.cancelOrder`(다중 액터 = 코디/시스템): `status == PAID` 면 `cancel()` 대신 `markRefundPending()` + `OrderCancelledEvent` 발행. 그 외 상태는 기존 `cancel()` 경로.
  - **고객 self-cancel(`cancelOrderByCustomer`)은 PAID 환불 분기를 타지 않는다** — PAID 는 `isCancellable` 가 아니므로 `cancel()` 이 거부. 픽업 후 고객 직접 취소 차단 정책 유지.
- **Payment 신규 consumer 핸들러** `PaymentSagaHandler.onOrderCancelled`: 해당 주문의 완료된(`COMPLETED`) 결제가 있으면 `requestRefund` 자동 호출, 없으면 skip(선결제 없는 취소엔 환불 없음 — **throw 금지 = DLQ 회피**).
- `PaymentEventConsumer` 에 `carry.Order.events` 구독 추가(groupId `carry-payment-module`) + `OrderCancelledEvent` 라우팅.
- `OrderCancelledEvent` 재사용으로 dispatch/delivery 캐스케이드가 동시에 동작.
- **재결제 다중 행 처리**: `requestPayment` 는 재시도 시 새 `Payment` 행을 만든다(주문당 FAILED + COMPLETED 다수 행 가능). 단수 `findByOrderId` 가 모호해지므로 **최신 행 우선 반환**(`findFirstByOrderIdOrderByIdDesc`)으로 변경. "현재 결제" 의미론이 환불/조회/`isOrderPaid` 모두에 맞다.

### D. 재결제 시한 스위퍼 (좀비 종결)

신규 `@Scheduled` 컴포넌트(carry-order): `status == PAYMENT_FAILED AND updatedAt < now - 24h` → `cancelOrder(SYSTEM, "재결제 시한 초과")`. 상태 가드로 멱등. `OrderPersistencePort.findByStatusAndUpdatedAtBefore` 추가. 24h 는 설정 상수.

> ⚠️ 멀티 인스턴스 중복 실행은 전이 가드로 안전하나, `ShedLock` 은 후속 하드닝(기존 갭 B2)으로 명시한다.

### E. 물리적 세탁물 / 알림 경계

- 픽업 후 종료 보상 시 세탁물 반환은 이벤트+상태로만 신호(반환 로지스틱스 모델링 제외 = 운영 책임).
- `PaymentFailedEvent` → 재결제 알림: 기존 `NotificationSagaHandler` 이벤트 구동에 `onPaymentFailed` 추가(`NotificationType.PAYMENT_FAILED`). consumer 는 이미 `carry.Payment.events` 구독 중.

### F. 검증 (TDD)

- 도메인 단위: 신규 전이/가드(`OrderStatusTest`, `OrderTest`).
- 핸들러 단위: `onPaymentFailed`, `onRefundCompleted`(`OrderSagaHandlerTest`); `onOrderCancelled`(신규 `PaymentSagaHandlerTest`).
- **통합(Testcontainers)**:
  - `PaymentFailureSagaIntegrationTest`: 결제 실패 → `PAYMENT_FAILED` → 재결제 성공 → `PAID` / → 시한 초과 스위퍼 → `CANCELLED`.
  - `RefundSagaIntegrationTest`: `PAID` 역행 → `REFUND_PENDING` → 자동 환불 → `REFUNDED` + dispatch/delivery 캐스케이드.

### G. 문서

본 스펙 + `docs/06-saga.md` 갱신(보상 흐름 추가, 실제 상태머신 반영).

## 4. 핵심 파일

| 영역 | 파일 |
|------|------|
| 상태머신 | `carry-order/.../domain/vo/OrderEnums.kt`, `domain/model/Order.kt` |
| 핸들러 | `carry-order/.../application/service/OrderSagaHandler.kt` |
| 취소/환불 | `carry-order/.../application/service/OrderCommandService.kt` |
| 포트 | `carry-order/.../application/port/outbound/OrderPersistencePort.kt` |
| 스위퍼 | `carry-order/.../application/service/PaymentRetryDeadlineSweeper.kt` (신규) |
| 환불 | `carry-payment/.../application/service/PaymentSagaHandler.kt`, `PaymentCommandService.kt` |
| 구독 | `carry-payment/.../adapter/inbound/kafka/PaymentEventConsumer.kt` |
| 다중행 | `carry-payment/.../adapter/outbound/persistence/repository/PaymentJpaRepository.kt` |
| 알림 | `carry-notification/.../application/service/NotificationSagaHandler.kt` |
| 통합테스트 | `carry-app/src/test/.../saga/*IntegrationTest.kt` |

## 5. 보상 흐름 다이어그램

```
[결제 실패 경로]
INVOICED ──PaymentFailedEvent──▶ PAYMENT_FAILED
   │                                  │
   │                          ┌───────┴────────┐
   │                   재결제 성공          24h 시한 초과
   │                          │            (스위퍼)
   ▼                          ▼                │
 PAID ◀──────────────────────┘                ▼
                                          CANCELLED
                                     (OrderCancelledEvent
                                      → dispatch/delivery 캐스케이드,
                                       완료 결제 없으면 환불 skip)

[환불 경로]
PAID ──cancelOrder(코디/시스템)──▶ REFUND_PENDING
                                      │ OrderCancelledEvent
            ┌─────────────────────────┼───────────────────────┐
            ▼                         ▼                         ▼
   PaymentSagaHandler        DispatchSagaHandler        DeliverySagaHandler
   .onOrderCancelled         .onOrderCancelled          .onOrderCancelled
   (COMPLETED 결제 환불)        (가드 통과 시 취소)          (가드 통과 시 취소)
            │
            ▼ RefundCompletedEvent
   OrderSagaHandler.onRefundCompleted
            │
            ▼
        REFUNDED
```
