# carry-dispatch 모듈 설계

## 역할
1. **배차 관리**: 주문에 대한 캐리어 배정 (자동 아님, 수동/반자동)
2. **캐리어 구역 관리**: 행정구역 기반 담당 구역 설정
3. **일감 수락/거부**: 캐리어의 일감 수락 및 강제 배정 거부 처리
4. **페널티 관리**: 강제 배정 거부 시 페널티 이력 기록
5. **타임아웃 처리**: 수거 요청 시각 30분 전까지 미배차 시 자동 취소

---

## 도메인 모델

### Dispatch (Aggregate Root)

```kotlin
class Dispatch private constructor(
    val id: Long?,
    val orderId: Long,
    private var _status: DispatchStatus,
    private var _carrierId: Long?,
    val areaCode: String,
    val desiredPickupAt: Instant,
    private var _assignedBy: AssignedBy?,
    private var _assignedAt: Instant?,
    private var _acceptedAt: Instant?,
    private var _cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val carrierId get() = _carrierId
    val assignedBy get() = _assignedBy

    companion object {
        fun create(orderId: Long, areaCode: String, desiredPickupAt: Instant): Dispatch
        fun reconstitute(...): Dispatch
    }

    // 캐리어 자발적 수락
    fun claimByCarrier(carrierId: Long)

    // 코디네이터 강제 배정
    fun assignByCoordinator(carrierId: Long)

    // 캐리어의 강제 배정 수락
    fun acceptAssignment()

    // 캐리어의 강제 배정 거부 → PENDING으로 복귀
    fun rejectAssignment(): PenaltyRecord

    // 취소
    fun cancel(reason: String)

    // 타임아웃
    fun timeout()

    // 타임아웃 판정
    fun isExpired(): Boolean =
        status == DispatchStatus.PENDING &&
        Instant.now().isAfter(desiredPickupAt.minus(30, ChronoUnit.MINUTES))
}
```

### DispatchStatus (상태 머신)

```
PENDING ───────────────────────────────────────────► TIMEOUT
   │                                                   │
   ├── (캐리어 직접 수락)                                 │
   │   → ACCEPTED ──────────────────────────────────────┤
   │                                                   │
   ├── (코디네이터 강제 배정)                               │
   │   → ASSIGNED ─── (수락) → ACCEPTED                 │
   │        │                                          │
   │        └── (거부) → PENDING (+ 페널티)               │
   │                                                   │
   └── (코디네이터 취소 or 고객 취소)                        │
       → CANCELLED ◄──────────────────────────────────┘
```

```kotlin
enum class DispatchStatus {
    PENDING,    // 대기 중 (캐리어 배정 전)
    ASSIGNED,   // 코디네이터가 배정 (캐리어 수락 대기)
    ACCEPTED,   // 확정 (캐리어가 수락 or 직접 잡음)
    CANCELLED,  // 취소
    TIMEOUT;    // 시간 초과 자동 취소

    fun canTransitionTo(target: DispatchStatus): Boolean = when (this) {
        PENDING   -> target in setOf(ASSIGNED, ACCEPTED, CANCELLED, TIMEOUT)
        ASSIGNED  -> target in setOf(ACCEPTED, PENDING, CANCELLED)  // PENDING = 거부 후 복귀
        ACCEPTED  -> target == CANCELLED
        CANCELLED -> false
        TIMEOUT   -> false
    }
}

enum class AssignedBy {
    CARRIER,      // 캐리어 자발적 수락
    COORDINATOR,  // 코디네이터 강제 배정
}
```

### CarrierArea (Entity)

```kotlin
class CarrierArea private constructor(
    val id: Long?,
    val carrierId: Long,
    val areaCode: String,       // 행정구역 코드 (예: "1168010100" = 서울 강남구 역삼동)
    val areaName: String,       // 표시용 (예: "서울특별시 강남구 역삼동")
    private var _active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val active get() = _active

    companion object {
        fun create(carrierId: Long, areaCode: String, areaName: String): CarrierArea
        fun reconstitute(...): CarrierArea
    }

    fun deactivate()
    fun activate()
}
```

### PenaltyRecord (Entity)

```kotlin
class PenaltyRecord private constructor(
    val id: Long?,
    val carrierId: Long,
    val dispatchId: Long,
    val reason: PenaltyReason,
    val createdAt: Instant,
) {
    companion object {
        fun create(carrierId: Long, dispatchId: Long, reason: PenaltyReason): PenaltyRecord
    }
}

enum class PenaltyReason {
    REJECTED_FORCED_ASSIGNMENT,
}
```

### 도메인 예외

```kotlin
class DispatchNotFoundException
class DispatchNotPendingException
class DispatchAlreadyAcceptedException
class CarrierNotInAreaException(carrierId: Long, areaCode: String)
class CarrierAreaNotFoundException
```

---

## 애플리케이션 레이어

### 인바운드 포트

```kotlin
interface DispatchCommandUseCase {
    // 캐리어 자발적 수락
    fun claimDispatch(dispatchId: Long, carrierId: Long)

    // 코디네이터 강제 배정
    fun assignDispatch(dispatchId: Long, carrierId: Long)

    // 캐리어 수락/거부
    fun acceptAssignment(dispatchId: Long, carrierId: Long)
    fun rejectAssignment(dispatchId: Long, carrierId: Long)

    // 코디네이터 취소
    fun cancelDispatch(dispatchId: Long, reason: String)
}

interface DispatchQueryUseCase {
    fun getDispatch(dispatchId: Long): Dispatch
    fun getDispatchByOrder(orderId: Long): Dispatch?
    fun getAvailableDispatches(carrierId: Long): List<Dispatch>  // 캐리어 담당 구역 기준
    fun getDispatchesByCarrier(carrierId: Long): List<Dispatch>
}

interface CarrierAreaUseCase {
    fun registerArea(carrierId: Long, areaCode: String, areaName: String): CarrierArea
    fun removeArea(carrierId: Long, areaCode: String)
    fun getCarriersByArea(areaCode: String): List<Long>  // carrierId 목록
    fun getAreasByCarrier(carrierId: Long): List<CarrierArea>
}

// 이벤트 핸들러
interface DispatchSagaEventHandler {
    fun onOrderCreated(event: OrderCreatedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)
}
```

### 아웃바운드 포트

```kotlin
interface DispatchPersistencePort {
    fun save(dispatch: Dispatch): Dispatch
    fun findById(id: Long): Dispatch?
    fun findByOrderId(orderId: Long): Dispatch?
    fun findPendingByAreaCodes(areaCodes: List<String>): List<Dispatch>
    fun findExpiredPendingDispatches(): List<Dispatch>
}

interface CarrierAreaPersistencePort {
    fun save(carrierArea: CarrierArea): CarrierArea
    fun findByCarrierId(carrierId: Long): List<CarrierArea>
    fun findActiveByAreaCode(areaCode: String): List<CarrierArea>
    fun findByCarrierIdAndAreaCode(carrierId: Long, areaCode: String): CarrierArea?
    fun delete(id: Long)
}

interface PenaltyRecordPersistencePort {
    fun save(record: PenaltyRecord): PenaltyRecord
    fun findByCarrierId(carrierId: Long): List<PenaltyRecord>
    fun countByCarrierId(carrierId: Long): Long
}
```

### DispatchCommandService 핵심 로직

```kotlin
// 주문 생성 이벤트 수신 → 배차 생성
@Transactional
fun onOrderCreated(event: OrderCreatedEvent) {
    val dispatch = Dispatch.create(
        orderId = event.orderId,
        areaCode = event.areaCode,
        desiredPickupAt = event.desiredPickupAt,
    )
    val saved = dispatchPersistencePort.save(dispatch)
    // TODO: 해당 구역 캐리어들에게 알림 (carry-notification via event)
}

// 캐리어가 직접 일감 수락
@Transactional
fun claimDispatch(dispatchId: Long, carrierId: Long) {
    val dispatch = findDispatch(dispatchId)

    // 캐리어가 해당 구역을 담당하는지 확인
    val areas = carrierAreaPersistencePort.findByCarrierId(carrierId)
    check(areas.any { it.areaCode == dispatch.areaCode }) {
        throw CarrierNotInAreaException(carrierId, dispatch.areaCode)
    }

    dispatch.claimByCarrier(carrierId)
    dispatchPersistencePort.save(dispatch)

    // Outbox: DispatchAcceptedEvent
    outboxEventPublisher.publish(
        aggregateType = "Dispatch",
        aggregateId = dispatch.orderId.toString(),  // orderId를 파티션 키로
        eventType = "DispatchAcceptedEvent",
        payload = DispatchAcceptedEvent(dispatch.id!!, dispatch.orderId, carrierId)
    )
}
```

### 타임아웃 스케줄러

```kotlin
@Component
class DispatchTimeoutScheduler(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    // 1분마다 만료된 배차 확인
    @Scheduled(fixedRate = 60_000)
    @Transactional
    fun checkExpiredDispatches() {
        val expired = dispatchPersistencePort.findExpiredPendingDispatches()
        expired.forEach { dispatch ->
            dispatch.timeout()
            dispatchPersistencePort.save(dispatch)

            outboxEventPublisher.publish(
                aggregateType = "Dispatch",
                aggregateId = dispatch.orderId.toString(),
                eventType = "DispatchTimeoutEvent",
                payload = DispatchTimeoutEvent(dispatch.id!!, dispatch.orderId)
            )
        }
    }
}
```

---

## 어댑터 레이어

### REST Controller

```kotlin
// 캐리어 앱
@RestController
@RequestMapping("/api/v2/dispatches")
class DispatchCarrierController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
) {
    @GetMapping("/available")                          // 가용 일감 목록 (내 구역)
    @PostMapping("/{dispatchId}/claim")                // 일감 수락
    @PostMapping("/{dispatchId}/accept")               // 강제 배정 수락
    @PostMapping("/{dispatchId}/reject")               // 강제 배정 거부
    @GetMapping("/my")                                 // 내 배차 목록
}

// 코디네이터 운영 웹
@RestController
@RequestMapping("/api/v2/coordinator/dispatches")
class DispatchCoordinatorController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {
    @PostMapping("/{dispatchId}/assign")               // 캐리어 강제 배정
    @PostMapping("/{dispatchId}/cancel")               // 배차 취소
    @GetMapping("/carriers")                           // 구역별 캐리어 목록
}

// 캐리어 구역 관리
@RestController
@RequestMapping("/api/v2/carrier-areas")
class CarrierAreaController(
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {
    @PostMapping                                       // 구역 등록
    @DeleteMapping("/{areaCode}")                      // 구역 해제
    @GetMapping("/my")                                 // 내 구역 목록
}
```

---

## DB 스키마

```sql
-- 배차
CREATE TABLE dispatch_dispatches (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL,
    carrier_id      BIGINT,
    area_code       VARCHAR(20) NOT NULL,
    desired_pickup_at TIMESTAMPTZ NOT NULL,
    assigned_by     VARCHAR(20),
    assigned_at     TIMESTAMPTZ,
    accepted_at     TIMESTAMPTZ,
    cancel_reason   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispatch_order_id ON dispatch_dispatches(order_id);
CREATE INDEX idx_dispatch_status ON dispatch_dispatches(status);
CREATE INDEX idx_dispatch_carrier_id ON dispatch_dispatches(carrier_id);
CREATE INDEX idx_dispatch_area_code_status ON dispatch_dispatches(area_code, status);

-- 캐리어 담당 구역
CREATE TABLE dispatch_carrier_areas (
    id          BIGSERIAL PRIMARY KEY,
    carrier_id  BIGINT NOT NULL,
    area_code   VARCHAR(20) NOT NULL,
    area_name   VARCHAR(100) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(carrier_id, area_code)
);

CREATE INDEX idx_carrier_areas_carrier_id ON dispatch_carrier_areas(carrier_id);
CREATE INDEX idx_carrier_areas_area_code ON dispatch_carrier_areas(area_code, active);

-- 페널티 기록
CREATE TABLE dispatch_penalty_records (
    id          BIGSERIAL PRIMARY KEY,
    carrier_id  BIGINT NOT NULL,
    dispatch_id BIGINT NOT NULL REFERENCES dispatch_dispatches(id),
    reason      VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_penalty_carrier_id ON dispatch_penalty_records(carrier_id);
```

---

## 구역 코드 체계

행정구역 코드(법정동코드)를 사용한다. 예시:

| 코드 | 지역 |
|------|------|
| `1168010100` | 서울특별시 강남구 역삼동 |
| `1168010300` | 서울특별시 강남구 개포동 |
| `2871025000` | 울산광역시 남구 삼산동 |

> 추후 구역 기준이 변경될 수 있으므로 `areaCode`를 단순 문자열로 취급한다.
> 구역 체계가 바뀌면 CarrierArea의 코드 값만 마이그레이션하면 된다.

---

## 테스트 전략

| 계층 | 테스트 | 도구 |
|------|--------|------|
| Domain | DispatchStatus 상태 전이, 타임아웃 판정, 거부 시 페널티 생성 | JUnit 5, AssertJ |
| Application | 배차 수락/거부/타임아웃 시나리오, 구역 검증 로직 | MockK |
| Architecture | 헥사고날 의존성 규칙 | ArchUnit |
