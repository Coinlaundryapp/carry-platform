# carry-delivery 모듈 설계

## 역할
캐리어가 배정받은 일감을 **실제로 수행하는 워크플로우**를 관리한다.
수거 → 계량 → 세탁 → 건조 → 배달의 각 단계를 추적하고,
단계별 사진 업로드를 기록한다.

> carry-dispatch = "누구에게 일감을 줄 것인가" (배정)
> carry-delivery = "배정받은 캐리어가 어떻게 일을 수행하는가" (실행)

---

## 도메인 모델

### Delivery (Aggregate Root)

```kotlin
class Delivery private constructor(
    val id: Long?,
    val orderId: Long,
    val dispatchId: Long,
    val carrierId: Long,
    val laundromatId: Long,
    private var _status: DeliveryStatus,
    private var _actualWeight: BigDecimal?,
    private val _steps: MutableList<DeliveryStep>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val actualWeight get() = _actualWeight
    val steps: List<DeliveryStep> get() = _steps.toList()

    companion object {
        fun create(
            orderId: Long,
            dispatchId: Long,
            carrierId: Long,
            laundromatId: Long,
        ): Delivery  // 생성 시 모든 DeliveryStep을 PENDING으로 초기화

        fun reconstitute(...): Delivery
    }

    // 수거 완료 (무게 입력 필수)
    fun completePickup(weight: BigDecimal, photoIds: List<Long>)

    // 세탁 시작
    fun startWashing(photoIds: List<Long>)

    // 건조 완료
    fun completeDrying(photoIds: List<Long>)

    // 배달 완료 (결제 완료 여부는 서비스 레이어에서 확인)
    fun completeDelivery(photoIds: List<Long>)

    // 취소
    fun cancel()

    // 현재 진행 중인 단계
    fun currentStep(): DeliveryStep?

    // 특정 단계의 사진 조회
    fun getPhotosForStep(stepType: DeliveryStepType): List<Long>
}
```

### DeliveryStatus (상태 머신)

```
PICKUP_PENDING ──────────────────────────────────► CANCELLED
       │
       │ completePickup(weight, photos)
       ▼
   PICKED_UP
       │
       │ startWashing(photos)
       ▼
   IN_LAUNDRY
       │
       │ completeDrying(photos)
       ▼
 LAUNDRY_COMPLETE
       │
       │ completeDelivery(photos)  ← 결제 완료 확인 필요
       ▼
   DELIVERED
```

```kotlin
enum class DeliveryStatus {
    PICKUP_PENDING,    // 수거 대기
    PICKED_UP,         // 수거 완료 (무게 측정됨)
    IN_LAUNDRY,        // 세탁/건조 진행 중
    LAUNDRY_COMPLETE,  // 세탁/건조 완료
    DELIVERED,         // 배달 완료
    CANCELLED;         // 취소

    fun canTransitionTo(target: DeliveryStatus): Boolean = when (this) {
        PICKUP_PENDING   -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP        -> target in setOf(IN_LAUNDRY, CANCELLED)
        IN_LAUNDRY       -> target in setOf(LAUNDRY_COMPLETE, CANCELLED)
        LAUNDRY_COMPLETE -> target in setOf(DELIVERED, CANCELLED)
        DELIVERED        -> false
        CANCELLED        -> false
    }
}
```

### DeliveryStep (Entity)

각 단계의 상세 정보와 사진을 기록한다.

```kotlin
class DeliveryStep private constructor(
    val id: Long?,
    val deliveryId: Long?,
    val stepType: DeliveryStepType,
    private var _status: StepStatus,
    private val _mediaIds: MutableList<Long>,
    private var _note: String?,
    private var _completedAt: Instant?,
    val createdAt: Instant,
) {
    val status get() = _status
    val mediaIds: List<Long> get() = _mediaIds.toList()
    val note get() = _note
    val completedAt get() = _completedAt

    companion object {
        fun createPending(stepType: DeliveryStepType): DeliveryStep
    }

    fun complete(mediaIds: List<Long>, note: String? = null)
}

enum class DeliveryStepType {
    PICKUP,    // 수거
    WEIGHING,  // 계량
    WASHING,   // 세탁
    DRYING,    // 건조
    DELIVERY,  // 배달
}

enum class StepStatus {
    PENDING,
    COMPLETED,
}
```

### 도메인 예외

```kotlin
class DeliveryNotFoundException
class DeliveryNotInExpectedStatusException(expected: DeliveryStatus, actual: DeliveryStatus)
class DeliveryWeightRequiredException
class DeliveryPhotoRequiredException(stepType: DeliveryStepType)
class OrderNotPaidException(orderId: Long)  // 결제 미완료 시 배달 불가
```

---

## 애플리케이션 레이어

### 인바운드 포트

```kotlin
interface DeliveryCommandUseCase {
    // 수거 완료 (무게 + 사진 필수)
    fun completePickup(deliveryId: Long, carrierId: Long, weight: BigDecimal, photoIds: List<Long>)

    // 세탁 시작 (사진)
    fun startWashing(deliveryId: Long, carrierId: Long, photoIds: List<Long>)

    // 건조 완료 (사진)
    fun completeDrying(deliveryId: Long, carrierId: Long, photoIds: List<Long>)

    // 배달 완료 (사진, 결제 확인 후)
    fun completeDelivery(deliveryId: Long, carrierId: Long, photoIds: List<Long>)
}

interface DeliveryQueryUseCase {
    fun getDelivery(deliveryId: Long): Delivery
    fun getDeliveryByOrder(orderId: Long): Delivery?
    fun getDeliveriesByCarrier(carrierId: Long): List<Delivery>
    fun getDeliverySteps(deliveryId: Long): List<DeliveryStep>
}

// 이벤트 핸들러
interface DeliverySagaEventHandler {
    fun onDispatchAccepted(event: DispatchAcceptedEvent)  // Delivery 생성
    fun onOrderCancelled(event: OrderCancelledEvent)      // Delivery 취소
    fun onDispatchCancelled(event: DispatchCancelledEvent) // Delivery 취소
}
```

### 아웃바운드 포트

```kotlin
interface DeliveryPersistencePort {
    fun save(delivery: Delivery): Delivery
    fun findById(id: Long): Delivery?
    fun findByOrderId(orderId: Long): Delivery?
    fun findByCarrierId(carrierId: Long): List<Delivery>
}

interface DeliveryStepPersistencePort {
    fun saveAll(steps: List<DeliveryStep>): List<DeliveryStep>
    fun findByDeliveryId(deliveryId: Long): List<DeliveryStep>
}

// carry-payment 동기 조회
interface PaymentQueryPort {
    fun isOrderPaid(orderId: Long): Boolean
}

// carry-media 연동 (Phase 4에서 실제 구현)
// 지금은 mediaId만 저장하고, 실제 업로드는 별도 엔드포인트에서 처리
```

### DeliveryCommandService 핵심 로직

```kotlin
// 수거 완료
@Transactional
fun completePickup(deliveryId: Long, carrierId: Long, weight: BigDecimal, photoIds: List<Long>) {
    val delivery = findDeliveryForCarrier(deliveryId, carrierId)

    // 도메인 로직: 상태 전이 + 무게 기록 + 사진 기록
    delivery.completePickup(weight, photoIds)
    deliveryPersistencePort.save(delivery)

    // Outbox: PickupCompletedEvent (carry-order, carry-payment가 수신)
    outboxEventPublisher.publish(
        aggregateType = "Delivery",
        aggregateId = delivery.orderId.toString(),
        eventType = "PickupCompletedEvent",
        payload = PickupCompletedEvent(
            deliveryId = delivery.id!!,
            orderId = delivery.orderId,
            carrierId = carrierId,
            actualWeight = weight,
            // carry-payment가 가격 계산에 필요한 정보도 포함
        )
    )
}

// 배달 완료
@Transactional
fun completeDelivery(deliveryId: Long, carrierId: Long, photoIds: List<Long>) {
    val delivery = findDeliveryForCarrier(deliveryId, carrierId)

    // 결제 완료 여부 확인 (carry-payment sync port)
    check(paymentQueryPort.isOrderPaid(delivery.orderId)) {
        throw OrderNotPaidException(delivery.orderId)
    }

    delivery.completeDelivery(photoIds)
    deliveryPersistencePort.save(delivery)

    // Outbox: DeliveryCompletedEvent
    outboxEventPublisher.publish(
        aggregateType = "Delivery",
        aggregateId = delivery.orderId.toString(),
        eventType = "DeliveryCompletedEvent",
        payload = DeliveryCompletedEvent(delivery.id!!, delivery.orderId, carrierId)
    )
}
```

---

## 어댑터 레이어

### REST Controller (캐리어 앱)

```kotlin
@RestController
@RequestMapping("/api/v2/deliveries")
class DeliveryController(
    private val deliveryCommandUseCase: DeliveryCommandUseCase,
    private val deliveryQueryUseCase: DeliveryQueryUseCase,
) {
    // 내 배달 목록
    @GetMapping("/my")
    fun getMyDeliveries(@AuthCarrierId carrierId: Long): List<DeliveryResponse>

    // 배달 상세 (단계별 사진 포함)
    @GetMapping("/{deliveryId}")
    fun getDelivery(@PathVariable deliveryId: Long): DeliveryDetailResponse

    // 수거 완료 (무게 + 사진)
    @PostMapping("/{deliveryId}/pickup")
    fun completePickup(
        @PathVariable deliveryId: Long,
        @RequestBody request: CompletePickupRequest,  // weight, photoIds
    )

    // 세탁 시작 (사진)
    @PostMapping("/{deliveryId}/washing")
    fun startWashing(
        @PathVariable deliveryId: Long,
        @RequestBody request: StepPhotoRequest,  // photoIds
    )

    // 건조 완료 (사진)
    @PostMapping("/{deliveryId}/drying")
    fun completeDrying(
        @PathVariable deliveryId: Long,
        @RequestBody request: StepPhotoRequest,
    )

    // 배달 완료 (사진)
    @PostMapping("/{deliveryId}/delivery")
    fun completeDelivery(
        @PathVariable deliveryId: Long,
        @RequestBody request: StepPhotoRequest,
    )
}
```

### 고객용 주문 진행 조회 (carry-order에서 sync port로 호출)

고객이 주문 상세에서 배달 진행 상태와 사진을 보는 것은
carry-order가 carry-delivery의 `DeliveryQueryPort`를 통해 조회한다.

```kotlin
// carry-order의 아웃바운드 포트
interface DeliveryQueryPort {
    fun getDeliveryStepsByOrderId(orderId: Long): List<DeliveryStepDto>
}
```

---

## DB 스키마

```sql
-- 배달
CREATE TABLE delivery_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL UNIQUE,
    dispatch_id     BIGINT NOT NULL,
    carrier_id      BIGINT NOT NULL,
    laundromat_id   BIGINT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    actual_weight   NUMERIC(10,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_order_id ON delivery_deliveries(order_id);
CREATE INDEX idx_delivery_carrier_id ON delivery_deliveries(carrier_id);
CREATE INDEX idx_delivery_status ON delivery_deliveries(status);

-- 배달 단계
CREATE TABLE delivery_steps (
    id              BIGSERIAL PRIMARY KEY,
    delivery_id     BIGINT NOT NULL REFERENCES delivery_deliveries(id),
    step_type       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    note            VARCHAR(500),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_steps_delivery_id ON delivery_steps(delivery_id);

-- 단계별 사진 참조 (media_id는 carry-media 모듈의 파일 ID)
CREATE TABLE delivery_step_media (
    id              BIGSERIAL PRIMARY KEY,
    delivery_step_id BIGINT NOT NULL REFERENCES delivery_steps(id),
    media_id        BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_step_media_step_id ON delivery_step_media(delivery_step_id);
```

---

## 사진 업로드 플로우

Phase 4에서 carry-media 모듈이 구현되기 전까지:

1. 프론트(캐리어 앱)에서 사진을 별도 API로 먼저 업로드 → mediaId 응답
2. 단계 완료 API 호출 시 mediaId 목록을 함께 전달
3. carry-delivery는 mediaId만 저장 (실제 파일 관리는 carry-media 담당)

```
캐리어 앱                carry-media (Phase 4)           carry-delivery
   │                          │                              │
   │── POST /media/upload ──►│                              │
   │◄── { mediaId: 42 } ─────│                              │
   │                          │                              │
   │── POST /deliveries/{id}/pickup ──────────────────────►│
   │   { weight: 5.2, photoIds: [42] }                      │
   │◄── 200 OK ──────────────────────────────────────────────│
```

> Phase 3에서는 carry-media가 아직 없으므로,
> mediaId를 그냥 Long 값으로 저장만 해둔다.
> 프론트에서 보내는 mediaId가 유효한지 검증하는 건 Phase 4에서 처리.

---

## 테스트 전략

| 계층 | 테스트 | 도구 |
|------|--------|------|
| Domain | DeliveryStatus 상태 전이, 수거 시 무게 필수 검증, 사진 기록 | JUnit 5, AssertJ |
| Application | 수거→세탁→건조→배달 전체 플로우, 결제 미완료 시 배달 차단 | MockK |
| Architecture | 헥사고날 의존성 규칙 | ArchUnit |
