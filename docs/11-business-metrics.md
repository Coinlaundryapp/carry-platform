# 11. 비즈니스 메트릭 카탈로그

> 최종 수정일: 2026-05-29
> 상태: Phase 3.1 — Active

ROADMAP Phase 3.1 ("비즈니스 메트릭 활성화")의 산출물. 도메인 서비스가 `MetricsPort`(`carry-common`)를 통해 발행하는 모든 비즈니스 메트릭의 단일 사실원(single source of truth).

---

## 네이밍 규약

- **카운터**: `carry.{domain}.{event}` — Prometheus가 자동으로 `_total` 접미사를 부여한다.
- **타이머**: `carry.{domain}.{event}.duration` — Prometheus가 `_seconds_count`, `_seconds_sum`, `_seconds_max`를 생성한다.
- **게이지**: `carry.{domain}.{event}` (단위가 자연스러운 경우 그대로, 그렇지 않으면 단위 접미사).

`carry.` 접두사로 운영팀이 자체 메트릭과 인프라(Spring Boot Actuator, JVM, Kafka)에서 자동 노출되는 메트릭을 명확히 구분한다.

### 태그 카디널리티 정책

- enum 값(예: `CancelledBy`, `PgProvider`) → 안전한 태그 (3~10개)
- 자유 텍스트(예: 취소 사유, 에러 메시지) → **태그 금지**. 시계열 폭발 유발.
- ID(orderId, customerId 등) → **태그 금지**. 추적은 traceId(분산 추적)로.

---

## 카탈로그

### 주문 (Order)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.order.created` | Counter | — | `OrderCommandService.createOrder` |
| `carry.order.cancelled` | Counter | `by={CUSTOMER\|COORDINATOR\|SYSTEM}` | `OrderCommandService.cancelOrder` |

### 배차 (Dispatch)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.dispatch.accepted` | Counter | `via={claim\|assignment}` | `DispatchCommandService.claimDispatch` / `acceptAssignment` |
| `carry.dispatch.rejected` | Counter | — | `DispatchCommandService.rejectAssignment` |

> **`via` 태그 해석**: `claim`은 캐리어가 PENDING 배차를 직접 잡은 경로(self-service), `assignment`는 코디네이터가 지정한 ASSIGNED 배차를 캐리어가 수락한 경로(orchestrated). 두 경로의 비율을 추적해 자동 배차 vs 수동 배차의 상대 빈도를 본다.

### 배달 (Delivery)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.delivery.completed` | Counter | — | `DeliveryCommandService.completeDelivery` |
| `carry.delivery.duration` | Timer | — | `DeliveryCommandService.completeDelivery` |

> **`carry.delivery.duration` 정의**: `Delivery` aggregate가 생성된 시각(=`DispatchAcceptedEvent` 사가 처리 시점)부터 배달이 완료된 시각까지의 wall-clock duration. 주문 생성부터 배달 완료까지의 전체 사가 길이는 아니며, 그것은 `carry.saga.duration`(아직 미구현, [known-debts] 참조)에서 다룬다.

### 결제 (Payment)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.payment.success` | Counter | `pg={TOSS_PAYMENTS\|KAKAO_PAY\|...}` | `PaymentCommandService.requestPayment` (성공 분기) |
| `carry.payment.failure` | Counter | `pg={TOSS_PAYMENTS\|KAKAO_PAY\|...}` | `PaymentCommandService.requestPayment` (실패 분기) |

> **`pg` 태그**: `PgProvider` enum값. PG별 성공률/실패율 분리 추적과 PG Circuit Breaker(`carry.resilience4j.circuitbreaker.*`)와의 상관 분석에 사용.

### 인프라 (Infrastructure)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.outbox.pending` | Gauge | — | `OutboxMetricsScheduler` (30초 주기) |
| `carry.kafka.dlq` | Counter | `topic`, `exception` | `KafkaConfig.wrapWithMetrics` |

> **`carry.outbox.pending`**: CDC가 아직 발행하지 않은 outbox 이벤트 수. 지속적으로 증가하면 Debezium 정체 신호.

---

## Phase 3.4 알럿 기준선 연계

ROADMAP Phase 3.4의 알럿 기준선이 참조할 카운터/게이지를 미리 정렬해 둠.

| 알럿 조건 | 사용 메트릭 |
|---|---|
| 결제 실패율 > 5% | `rate(carry_payment_failure_total) / (rate(carry_payment_success_total) + rate(carry_payment_failure_total))` |
| DLQ 메시지 수 > 0 | `increase(carry_kafka_dlq_total[5m])` |
| 배차 거부율 > 20% | `rate(carry_dispatch_rejected_total) / (rate(carry_dispatch_accepted_total) + rate(carry_dispatch_rejected_total))` |
| 주문 생성률 전일 대비 50%↓ | `rate(carry_order_created_total[1h])` vs 24h 전 |
| Outbox 적재 누적 | `carry_outbox_pending` |

---

## 미구현 항목 (의도적 보류)

ROADMAP 3.1 메트릭 목록 중 본 PR에서 미구현된 항목:

| 메트릭 | 사유 |
|---|---|
| `carry.dispatch.timeout` | 배차 만료를 트리거할 스케줄러가 아직 없음. `DispatchPersistencePort.findExpiredPendingDispatches`만 존재하고 호출자 없음. 스케줄러 도입 시 동반 추가. |
| `carry.saga.duration` | 주문 생성 시각을 배달 완료 컨텍스트로 전파하는 메커니즘이 필요(이벤트 페이로드 확장 or stateful collector). 별도 PR. |

자세한 후속 작업은 [의도적 빚 추적][known-debts]에서 관리.

[known-debts]: ../README.md
