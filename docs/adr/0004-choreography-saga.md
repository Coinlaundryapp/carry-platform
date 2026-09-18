# ADR-0004: Choreography Saga 채택

## 상태

Accepted

## 날짜

2026-06-09

## 맥락

주문 한 건은 여러 모듈(`order`·`dispatch`·`delivery`·`payment`)에 걸친 장기 워크플로우다:
생성 → 배차 → 수거 → 청구 → 결제 → 세탁/배달 → 완료. 각 단계는 서로 다른 애그리거트의
상태 전이를 일으키고, 중간에 실패하면 보상(취소·환불)이 필요하다.

### 문제 정의

여러 모듈에 걸친 비즈니스 트랜잭션을 어떻게 조정할 것인가. 단일 ACID 트랜잭션으로 묶을 수 없는
(서로 다른 애그리거트·모듈 경계) 분산 작업이므로 Saga 패턴이 필요하다. Saga의 두 형태 중
하나를 택해야 한다: 중앙 오케스트레이터가 단계를 지시하는 **Orchestration**, 또는 각 참여자가
이벤트에 반응해 자기 책임을 수행하는 **Choreography**.

### 제약

- 도메인 모듈 간 직접 의존 금지(→ [ADR-0003](0003-module-decomposition-criteria.md))
- 이벤트 전파는 Outbox+CDC로 신뢰성·순서 보장(→ [ADR-0002](0002-outbox-cdc-over-dual-write.md))
- 각 애그리거트는 자신의 상태만 소유·변경(분산 트랜잭션·분산 락 회피)
- 분산 시스템 패턴 시연이 1차 목표

## 결정

**Choreography Saga를 채택한다.** 중앙 오케스트레이터를 두지 않고, 각 모듈이 Kafka 토픽을 구독해
자신의 애그리거트 상태를 전이시키고 다음 도메인 이벤트를 발행한다. 보상은 역방향 이벤트
(`OrderCancelledEvent`·`RefundCompletedEvent` 등)의 캐스케이드로 구현한다.

### 핵심 결정 사항

1. **오케스트레이터 부재** — `Orchestrator`/`SagaManager` 클래스가 없다. 각 모듈은 자신의
   `*SagaEventHandler`(인바운드 포트)와 `*SagaHandler`(서비스 구현)를 가지며, `*EventConsumer`가
   `@KafkaListener`로 토픽을 구독해 핸들러에 위임한다.

2. **정상 경로(이벤트 체인)**
   ```
   OrderCreatedEvent     (order)    → DispatchSagaHandler.onOrderCreated     → Dispatch PENDING
   DispatchAcceptedEvent (dispatch) → OrderSagaHandler.onDispatchAccepted    → Order DISPATCHED
                                    → DeliverySagaHandler.onDispatchAccepted → Delivery PICKUP_PENDING
   PickupCompletedEvent  (delivery) → OrderSagaHandler.onPickupCompleted     → Order PICKED_UP
                                    → PaymentSagaHandler.onPickupCompleted   → Invoice 발행
   InvoiceIssuedEvent    (payment)  → OrderSagaHandler.onInvoiceIssued       → Order INVOICED
   PaymentCompletedEvent (payment)  → OrderSagaHandler.onPaymentCompleted    → Order PAID
   LaundryStartedEvent   (delivery) → OrderSagaHandler.onLaundryStarted      → Order IN_PROGRESS
   DeliveryCompletedEvent(delivery) → OrderSagaHandler.onDeliveryCompleted   → Order COMPLETED
   ```

3. **상태는 각 애그리거트에 분산** — Saga 진행 상태를 담는 중앙 테이블이 없다. `Order`·`Dispatch`·
   `Delivery`·`Payment` 각자의 상태 머신(`canTransitionTo`)이 곧 Saga의 분산 상태다.

4. **보상 트랜잭션 = 역방향 이벤트 캐스케이드** (PR #72)
   - 결제 실패: `PaymentFailedEvent` → Order `PAYMENT_FAILED`. 24h 내 재결제 없으면
     `PaymentRetryDeadlineSweeper`(@Scheduled)가 자동 취소.
   - PAID 취소: Order `REFUND_PENDING` + `OrderCancelledEvent` → `PaymentSagaHandler`가 완료 결제에
     한해 자동 환불(`RefundCompletedEvent`) + `Dispatch`/`Delivery`로 취소 캐스케이드.

5. **멱등·순서 안전한 핸들러** — 모든 핸들러가 상태 가드(`if (order.status == INVOICED)` 등)로
   늦게 도착한/중복 이벤트를 무시. Outbox의 `aggregate_id` 키 파티셔닝이 애그리거트별 순서를 보장.

### 구현 세부

```kotlin
// 멱등·순서 가드의 전형 (OrderSagaHandler.onPaymentFailed)
override fun onPaymentFailed(event: PaymentFailedEvent) {
    val order = findOrder(event.orderId)
    // INVOICED 에서만 전이 — 이미 PAID/취소/환불된 주문에 늦게 도착한 실패 이벤트는 무시
    if (order.status == OrderStatus.INVOICED) {
        order.markPaymentFailed()
        orderPersistencePort.save(order)
    }
}
```

토픽 구독 분포: `carry-order`는 Dispatch/Delivery/Payment 토픽을, `carry-dispatch`는 Order 토픽을,
`carry-delivery`는 Dispatch/Order 토픽을, `carry-payment`는 Delivery/Order 토픽을 구독한다
(모듈별 `groupId`로 격리).

## 결과

### 긍정적

- **느슨한 결합**: 각 모듈은 자신이 구독하는 이벤트와 발행하는 이벤트만 알면 됨.
  중앙 조정자가 모든 모듈을 알 필요가 없음 → [ADR-0003](0003-module-decomposition-criteria.md)의
  "도메인 간 직접 의존 금지"와 자연스럽게 부합.
- **독립 확장·추가**: 새 참여자는 기존 이벤트를 구독하기만 하면 됨(기존 모듈 수정 불필요).
- **SPOF 없음**: 워크플로우 제어가 분산되어 중앙 조정자 장애점이 없음.
- **분산 상태 머신 시연**: 각 애그리거트의 상태 머신 + 멱등 가드가 분산 합의 없이 일관성을 유지.

### 부정적

- **전역 흐름 가시성 저하**: 워크플로우가 여러 모듈의 핸들러에 흩어져 있어 "전체 그림"을 한 곳에서
  볼 수 없음 → 분산 추적(trace_id 전파)·통합 테스트로 보완.
- **순환 이벤트 의존 위험**: 모듈 간 이벤트 구독이 얽히면 추론이 어려워질 수 있음.
- **보상 로직 분산**: 실패 경로가 여러 핸들러에 분산되어 전체 보상 시나리오 파악에 노력 필요.

### 위험

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| 흩어진 흐름으로 디버깅 난이도 | 중 | 중 | `trace_id` 전파(Outbox) + 4개 Saga 통합 테스트(정상 17단계·취소 4·결제실패/보상 5 시나리오) |
| 멱등/순서 위반으로 잘못된 전이 | 저 | 고 | 상태 머신 `canTransitionTo` + 핸들러 상태 가드 + `aggregate_id` 파티셔닝 |
| 보상 누락(부분 실패가 보상 안 됨) | 저 | 고 | 결제 재시도 스위퍼·취소 캐스케이드를 통합 테스트로 검증(PR #72) |

## 고려한 대안

### Orchestration Saga (중앙 오케스트레이터)

`OrderSagaOrchestrator`가 각 단계를 명령(command)으로 지시하고 응답을 받아 다음 단계를 결정.

**장점:**
- 전체 워크플로우가 한 곳에 명시적으로 표현됨 → 가독성·디버깅 용이
- 보상 시퀀스를 중앙에서 일관되게 제어

**단점:**
- 오케스트레이터가 모든 참여 모듈(상태·명령)을 알아야 함 → 강한 결합
- 참여자 추가/변경 시 오케스트레이터 수정 필요
- 오케스트레이터가 워크플로우의 SPOF·병목

**기각 이유:** 도메인 모듈 간 직접 의존을 금지한 모듈 경계 결정([ADR-0003])과 충돌한다.
중앙 조정자가 모든 모듈을 알아야 하는 결합이 본 프로젝트가 시연하려는 "느슨하게 결합된
이벤트 기반 분산 워크플로우"와 반대 방향이다. 가시성 저하는 분산 추적과 통합 테스트로 보완한다.

## 참조

- `carry-order/src/main/kotlin/com/carry/order/application/service/OrderSagaHandler.kt`
- `carry-dispatch/.../DispatchSagaHandler.kt`, `carry-delivery/.../DeliverySagaHandler.kt`,
  `carry-payment/.../PaymentSagaHandler.kt`
- `carry-event/src/main/kotlin/com/carry/event/` (이벤트 정의)
- `carry-order/src/main/kotlin/com/carry/order/domain/vo/OrderEnums.kt` (상태 머신)
- `carry-order/.../application/service/PaymentRetryDeadlineSweeper.kt`
- `carry-app/src/test/kotlin/com/carry/app/saga/` (Saga 통합 테스트 4종)
- 관련: [ADR-0002 Outbox+CDC](0002-outbox-cdc-over-dual-write.md),
  [ADR-0003 모듈 분리 기준](0003-module-decomposition-criteria.md)
