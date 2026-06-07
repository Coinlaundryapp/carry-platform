# Clock 주입 — 시간 처리 (ROADMAP 5.1)

> 작성일: 2026-06-07 · 브랜치: `feature/clock-injection` · base develop `7124b7c`
> 백로그 P1 #3 — `2026-06-07-carry-remaining-backlog.md`

## 1. 목표 / 문제

도메인·애플리케이션 레이어가 `Instant.now()`를 직접 호출 → 시간 의존 로직(만료·생성/취소/완료 시각·sweep cutoff·메트릭 duration)의 **결정적 테스트 불가**. 호출마다 미세하게 다른 시각이 박혀 한 트랜잭션 내에서도 `createdAt ≠ updatedAt` 같은 미세 불일치 가능.

`Clock`을 주입해 (1) 시간을 외부에서 고정 가능하게 만들고 (2) 한 트랜잭션 내 시각을 단일화한다. 이후 멱등성/성능/만료 테스트(P2)의 선행 기반.

## 2. 스코프

**대상 (~30 호출, 2계층)**
- **순수 도메인 모델 14개**: Order, Dispatch, PenaltyRecord, CarrierArea, Delivery, DeliveryStep, Payment, Invoice, Review, Term, OperationEvent, Notification, DeviceToken, MediaResource.
- **애플리케이션 서비스 16개**: OrderCommandService, OrderSagaHandler, PaymentRetryDeadlineSweeper, DispatchCommandService, DispatchSagaHandler, CarrierAreaService, DeliveryCommandService, DeliverySagaHandler, PaymentCommandService, InvoiceService, ReviewCommandService, NotificationCommandService, DeviceTokenCommandService, TermCommandService, OperationSagaHandler, MediaUploadService.

**범위 밖 (의도적 — 프레임워크/인프라 시각)**
- `BaseEntity`(JPA `@CreatedDate`/auditing 영역), `OutboxEvent.createdAt`, `ProcessedEvent.processedAt`, `DomainEvent.occurredAt`(기본 파라미터), `OperationEventJpaEntity.createdAt`, `AuditPersistenceAdapter.occurredAt`. 별도 설계 사안.

## 3. 아키텍처 — 3계층 시간 흐름

```
ClockConfig (@Bean Clock = Clock.systemUTC())   ← carry-app/config, 단일 정의
        │ 생성자 주입
        ▼
ApplicationService (@Service)                     ← private val clock: Clock
  val now = clock.instant()                       ← 명령 메서드 진입 시 1회
        │ now: Instant 전달
        ▼
Domain factory / transition method                ← 순수, java.time.Clock 의존 0
  Order.create(..., now)  /  order.cancel(reason, by, now)
        │ now 체이닝
        ▼
중첩 도메인                                          ← PenaltyRecord.create(..., now)
                                                      DeliveryStep.complete(..., now)
```

## 4. 핵심 결정

1. **도메인은 `now: Instant`만 받음** — `java.time.Clock` import 0건 유지. 도메인은 시간을 *입력*으로 받는 순수 함수. factory(`create(..., now)`)와 시각을 쓰는 전이 메서드(`markCompleted(now)`, `cancel(reason, by, now)` 등)에 `now: Instant`를 **마지막 파라미터**로 추가.
2. **중첩 도메인은 now 체이닝** — 부모가 받은 `now`를 자식 factory/메서드에 전달:
   - `Dispatch.rejectAssignment(now)` → `PenaltyRecord.create(..., now)`
   - `Delivery.completePickup/startWashing/completeDrying/completeDelivery(..., now)` → `DeliveryStep.complete(..., now)`
   자식은 따로 `Instant.now()` 호출 안 함.
3. **앱서비스가 유일한 `clock.instant()` 호출점** — 명령 메서드 진입 시 `val now = clock.instant()` 1회 계산 → 같은 트랜잭션 내 모든 도메인 시각 동일(`createdAt == updatedAt` 보장). **미세 동작 변화이며 의도된 개선**.
4. **`Clock` 빈** — `carry-app/config/ClockConfig.kt`에 `@Bean fun clock(): Clock = Clock.systemUTC()`. 기존 `InMemoryRefreshTokenStore`의 `Clock.systemUTC()` 선례와 일치(`Instant`는 TZ 무관). 컴포넌트 스캔이 전 모듈 `@Service`에 주입.
5. **시간 쿼리 메서드도 파라미터화** — `Dispatch.isExpired()` → `isExpired(now: Instant)`. 호출 서비스가 `clock.instant()` 전달. 만료 판정의 결정적 테스트 가능.
6. **앱서비스 2건 직접 호출 치환** — `DeliveryCommandService:124`(메트릭 duration, 명시적 TODO 해소) → `clock.instant()`; `PaymentRetryDeadlineSweeper:33`(scheduled sweep cutoff) → `clock.instant().minus(...)`.

## 5. 시그니처 변경 표 (도메인)

| 모델 | 메서드 | 변경 |
|---|---|---|
| Order | `create(...)`, `markCompleted()`, `cancel(reason, by)` | `+ now: Instant` |
| Dispatch | `create(...)`, `claimByCarrier(id)`, `assignByCoordinator(id)`, `acceptAssignment()`, `rejectAssignment()`, `isExpired()` | `+ now: Instant` |
| PenaltyRecord | `create(...)` | `+ now: Instant` (Dispatch가 전달) |
| CarrierArea | `create(...)` | `+ now: Instant` |
| Delivery | `create(...)`, `completePickup/startWashing/completeDrying/completeDelivery(...)` | `+ now: Instant` |
| DeliveryStep | `complete(...)` | `+ now: Instant` (Delivery가 전달) |
| Payment | `create(...)`, `markCompleted(pgTxId)` | `+ now: Instant` |
| Invoice | `create(...)` | `+ now: Instant` |
| Review | `create(...)`, `update(...)`, `addMedia(url)` | `+ now: Instant` |
| Term | `create(...)`, `update(...)`, `deactivate()`, `activate()` | `+ now: Instant` |
| OperationEvent | `create(...)` | `+ now: Instant` |
| Notification | `create(...)`, `markSent()` | `+ now: Instant` |
| DeviceToken | `create(...)`, `refresh(userId)` | `+ now: Instant` |
| MediaResource | `create(...)` | `+ now: Instant` |

`reconstitute()`는 저장된 시각을 받으므로 **불변**.

## 6. 테스트 전략 (TDD)

- **신규 결정적 단언**: 영향 받는 각 도메인에 `Clock.fixed(FIXED_INSTANT, UTC)`(서비스 레벨) 또는 고정 `now` 인자(도메인 레벨)로 `createdAt`/`cancelledAt`/`paidAt`/만료 판정 등을 **정확한 값**으로 단언하는 테스트 1개 이상 추가.
- **기존 도메인 테스트 갱신**: `*Test.kt`의 factory/전이 호출부(Order/Dispatch/Delivery/Payment/Invoice/Review/Term/Notification/DeviceToken/MediaResource 등)에 고정 `Instant` 인자 추가. require→500 롤아웃 때와 동형(대량 호출부 정합).
- **서비스 테스트**: Clock 빈을 `Clock.fixed(...)` mock/stub으로 주입. `PaymentRetryDeadlineSweeper`는 고정 now로 cutoff 경계(deadline 직전/직후 주문) 결정적 단언.
- **Testcontainers saga IT**: 시그니처 변경에 따른 호출부 정합만 필요(로직 불변). `:carry-app:test` GREEN 유지.

## 7. 검증 게이트 (순서)

1. 전체 `compileTestKotlin` — 크로스모듈/saga 호출부 깨짐 검출(유닛테스트 못 잡음).
2. 영향 모듈 `:test`.
3. `:carry-app:test`(Testcontainers saga IT).
4. **머지 전 라이브 풀스택 스모크** — docker(postgres 5442 + redis + kafka), `bootRun`에 `MANAGEMENT_TRACING_ENABLED=false` 필수.

## 8. 리스크 / 주의

- **호출부 누락 → 컴파일 에러**: 시그니처 변경이라 컴파일러가 전부 잡음(안전). `compileTestKotlin`이 1차 그물.
- **BaseEntity 정합**: `updatedAt`은 JPA auditing(`@LastModifiedDate`)이 별도 관리하므로 도메인 `now`와 영속 시각이 다를 수 있으나, 이는 기존에도 그러했고 본 작업 범위 밖. 도메인 `createdAt/updatedAt`은 도메인 의미의 시각, JPA auditing은 영속 시각으로 책임 분리 유지.
- **트랜잭션당 now 단일화(결정 #3)**: 동작 미세 변화. 의도된 개선이며 단언 강화로 보호.

## 9. 산출물

- `carry-app/config/ClockConfig.kt` (신규, `@Bean Clock`).
- 14개 도메인 모델 시그니처 변경 + `Instant.now()` 제거.
- 16개 애플리케이션 서비스 Clock 주입 + `now` 전달.
- 도메인/서비스 테스트 갱신 + 결정적 단언 추가.
- 이슈(한글) → PR(base develop). **dev 머지 = 사용자 게이트.**
