# 11. 비즈니스 메트릭 카탈로그

> 최종 수정일: 2026-06-13
> 상태: Active — #97·#99·#100/#112·#124·#125 반영

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
| `carry.dispatch.timeout` | Counter | — | `DispatchCommandService.timeoutDispatch` (DispatchTimeoutSweeper #97) |

> **`via` 태그 해석**: `claim`은 캐리어가 PENDING 배차를 직접 잡은 경로(self-service), `assignment`는 코디네이터가 지정한 ASSIGNED 배차를 캐리어가 수락한 경로(orchestrated). 두 경로의 비율을 추적해 자동 배차 vs 수동 배차의 상대 빈도를 본다.

### 배달 (Delivery)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.delivery.completed` | Counter | — | `DeliveryCommandService.completeDelivery` |
| `carry.delivery.duration` | Timer | — | `DeliveryCommandService.completeDelivery` |

> **`carry.delivery.duration` 정의**: `Delivery` aggregate가 생성된 시각(=`DispatchAcceptedEvent` 사가 처리 시점)부터 배달이 완료된 시각까지의 wall-clock duration. 주문 생성부터 배달 완료까지의 전체 사가 길이는 아니며, 그것은 아래 `carry.saga.duration`에서 다룬다.

### 사가 (Saga)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.saga.duration` | Timer | — | `OrderSagaHandler` (order.createdAt 기준 wall-clock, #99) |
| `carry.saga.stuck` | Counter | `status` | `StuckSagaDetector` (비종결 중간상태 정체 감지, #100/#112) |

> **`carry.saga.duration`**: 주문 생성(`order.createdAt`)부터 사가 종결까지의 전체 길이. `carry.delivery.duration`(배달 구간만)과 구분된다.
> **`carry.saga.stuck`**: 일정 시간 이상 중간 상태(PENDING 등 7종)에 머문 사가 수. `status` 태그로 어느 단계에서 막혔는지 분해. 알럿 룰 #9(SagaStuck)가 소비.

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
| `carry.kafka.dlq.redriven` | Counter | `topic` | `DlqRedriveService` (원본 토픽 재발행, #124) |
| `carry.kafka.dlq.parked` | Counter | `topic` | `DlqRedriveService` (재발행 한도 도달 보류, #124) |

> **`carry.outbox.pending`**: CDC가 아직 발행하지 않은 outbox 이벤트 수. 지속적으로 증가하면 Debezium 정체 신호.
> **`carry.kafka.dlq.redriven` / `.parked`**: 운영자 트리거 DLQ 재처리(`POST /api/v2/admin/dlq/redrive`)의 결과. redriven=원본 토픽 재발행, parked=재발행 3회 한도 도달로 DLQ 잔류(수동 검토 대상).

### 위치 (Geo)

| 메트릭 | 타입 | 태그 | 호출 위치 |
|---|---|---|---|
| `carry.geo.cache` | Counter | `direction={fwd\|rev}`, `result={hit\|miss}` | `RedisCachingGeocodingAdapter` / `…ReverseGeocodingAdapter` (#125) |

> **`carry.geo.cache`**: Redis 지오코딩 캐시 적중률. 적중률 = `hit / (hit+miss)`. 외부 Naver API 호출 절감과 24h TTL fallback 효과 측정.

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

## Timer/Histogram 노출 주의 (percentile-histogram)

`histogram_quantile`로 p50/p95/p99를 그리는 패널·알럿은 `_bucket` 시리즈를 전제한다. Micrometer는
`management.metrics.distribution.percentiles-histogram.<name>=true`를 켜야 `_bucket`을 노출한다 — 미설정 시
Timer가 `_count`/`_sum`만 내보내 quantile 패널이 **트래픽이 있어도 No data**가 된다(2026-06-12 라이브 검증 발견, #125).
현재 활성: `http.server.requests`, `carry.delivery.duration`, `carry.saga.duration` (`application.yml`).
새 Timer에 quantile 패널을 붙이려면 이 목록에 등록할 것.

## 이력

ROADMAP 3.1 초기 카탈로그에서 미구현이던 `carry.dispatch.timeout`(#97)·`carry.saga.duration`(#99)은 구현 완료.
이후 `carry.saga.stuck`(#100/#112)·`carry.kafka.dlq.redriven`/`.parked`(#124)·`carry.geo.cache`(#125)가 추가됐다.
의도적 후속은 루트 `ROADMAP.md` 진행 노트 + [`docs/adr/`](adr/) 참조.
