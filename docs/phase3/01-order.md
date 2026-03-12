# carry-order 모듈 설계

## 역할
주문의 생성부터 완료까지 **전체 라이프사이클을 추적**하는 사가의 중심 모듈.
주문 자체의 비즈니스 로직(생성, 취소)을 수행하고,
다른 모듈에서 발행한 이벤트를 수신하여 주문 상태를 갱신한다.

---

## 도메인 모델

### Order (Aggregate Root)

```kotlin
class Order private constructor(
    val id: Long?,
    val customerId: Long,
    private var _status: OrderStatus,
    val laundromatId: Long,
    val laundryItemType: LaundryItemType,
    val selectedOptions: List<SelectedOption>,
    val shippingAddress: OrderShippingAddress,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    private var _carrierId: Long?,
    private var _invoiceId: Long?,
    private var _totalAmount: Long?,
    private var _actualWeight: BigDecimal?,
    private var _cancelReason: String?,
    private var _cancelledAt: Instant?,
    private var _completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val carrierId get() = _carrierId
    val invoiceId get() = _invoiceId
    val totalAmount get() = _totalAmount
    val actualWeight get() = _actualWeight
    val cancelReason get() = _cancelReason

    companion object {
        fun create(...): Order  // 팩토리 메서드
        fun reconstitute(...): Order  // JPA → 도메인 복원
    }

    // 상태 전이 메서드
    fun markDispatched(carrierId: Long)
    fun markPickedUp(actualWeight: BigDecimal)
    fun markInvoiced(invoiceId: Long, totalAmount: Long)
    fun markPaid()
    fun markInProgress()
    fun markCompleted()
    fun cancel(reason: String)

    // 취소 가능 여부
    fun isCancellable(): Boolean
}
```

### OrderStatus (상태 머신)

```
CREATED ─────────────────────────────────────────────► CANCELLED
   │                                                      ▲
   │ (DispatchAcceptedEvent)                               │
   ▼                                                      │
DISPATCHED ──────────────────────────────────────────► CANCELLED
   │                                (고객 취소, 수거 전)
   │ (PickupCompletedEvent)
   ▼
PICKED_UP ──────────── (이후 취소 불가) ──────────────────
   │
   │ (InvoiceIssuedEvent)
   ▼
INVOICED
   │
   │ (PaymentCompletedEvent)
   ▼
PAID
   │
   │ (LaundryStartedEvent)
   ▼
IN_PROGRESS
   │
   │ (DeliveryCompletedEvent)
   ▼
COMPLETED
```

```kotlin
enum class OrderStatus {
    CREATED,
    DISPATCHED,
    PICKED_UP,
    INVOICED,
    PAID,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED     -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED  -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP   -> target == INVOICED
        INVOICED    -> target == PAID
        PAID        -> target == IN_PROGRESS
        IN_PROGRESS -> target == COMPLETED
        COMPLETED   -> false
        CANCELLED   -> false
    }
}
```

### Value Objects

```kotlin
// 주문 시점에 고객이 선택한 옵션 (가격은 나중에 계량 후 결정)
data class SelectedOption(
    val optionType: OptionType,      // WASH, DRY, ADDITIONAL
    val subOptionType: SubOptionType, // STANDARD, HOT_WATER, etc.
)

// 주문 시점 배송지 스냅샷 (사용자가 나중에 주소를 변경해도 주문에는 영향 없음)
data class OrderShippingAddress(
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String?,
    val latitude: Double,
    val longitude: Double,
    val recipientName: String,
    val recipientPhone: String,
    val entranceInfo: String?,
)
```

### 도메인 예외

```kotlin
class OrderNotFoundException
class InvalidOrderStatusTransitionException(from: OrderStatus, to: OrderStatus)
class OrderNotCancellableException(orderId: Long, currentStatus: OrderStatus)
```

---

## 애플리케이션 레이어

### 인바운드 포트

```kotlin
interface OrderCommandUseCase {
    fun createOrder(command: CreateOrderCommand): Order
    fun cancelOrder(orderId: Long, reason: String)
}

interface OrderQueryUseCase {
    fun getOrder(orderId: Long): Order
    fun getOrdersByCustomer(customerId: Long): List<Order>
}

// 이벤트 핸들러 (Kafka Consumer가 호출)
interface OrderSagaEventHandler {
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onDispatchTimeout(event: DispatchTimeoutEvent)
    fun onDispatchCancelled(event: DispatchCancelledEvent)
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onInvoiceIssued(event: InvoiceIssuedEvent)
    fun onPaymentCompleted(event: PaymentCompletedEvent)
    fun onPaymentFailed(event: PaymentFailedEvent)
    fun onDeliveryCompleted(event: DeliveryCompletedEvent)
}
```

### 아웃바운드 포트

```kotlin
interface OrderPersistencePort {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByCustomerId(customerId: Long): List<Order>
    fun findCancellableOrdersBeforePickupDeadline(deadline: Instant): List<Order>
}

// 다른 모듈 동기 조회용
interface UserQueryPort {
    fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress
}

interface LaundromatQueryPort {
    fun getLaundromatName(laundromatId: Long): String
}
```

### CreateOrderCommand

```kotlin
data class CreateOrderCommand(
    val customerId: Long,
    val shippingAddressId: Long,
    val laundromatId: Long,
    val laundryItemType: LaundryItemType,
    val selectedOptions: List<SelectedOption>,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
)
```

### OrderCommandService 핵심 로직

```kotlin
@Transactional
fun createOrder(command: CreateOrderCommand): Order {
    // 1. 배송지 조회 (carry-user sync port)
    val address = userQueryPort.getShippingAddress(command.customerId, command.shippingAddressId)

    // 2. 세탁소명 조회 (carry-laundromat sync port)
    val laundromatName = laundromatQueryPort.getLaundromatName(command.laundromatId)

    // 3. 주문 생성 (도메인 팩토리)
    val order = Order.create(command, address)

    // 4. 저장
    val saved = orderPersistencePort.save(order)

    // 5. Outbox 이벤트 발행
    outboxEventPublisher.publish(
        aggregateType = "Order",
        aggregateId = saved.id.toString(),
        eventType = "OrderCreatedEvent",
        payload = OrderCreatedEvent(...)
    )

    return saved
}
```

---

## 어댑터 레이어

### JPA Entity

```kotlin
@Entity
@Table(name = "orders")
class OrderJpaEntity(
    // ... BaseEntity 상속
    val customerId: Long,
    @Enumerated(EnumType.STRING)
    var status: OrderStatus,
    val laundromatId: Long,
    @Enumerated(EnumType.STRING)
    val laundryItemType: LaundryItemType,
    // 배송지 스냅샷 (embedded)
    @Embedded
    val shippingAddress: OrderShippingAddressEmbeddable,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    var carrierId: Long?,
    var invoiceId: Long?,
    var totalAmount: Long?,
    var actualWeight: BigDecimal?,
    var cancelReason: String?,
    var cancelledAt: Instant?,
    var completedAt: Instant?,
) : BaseEntity() {
    fun toDomain(): Order
    fun updateFrom(order: Order)
    companion object { fun fromDomain(order: Order): OrderJpaEntity }
}

@Entity
@Table(name = "order_selected_options")
class OrderSelectedOptionJpaEntity(
    val orderId: Long,
    @Enumerated(EnumType.STRING)
    val optionType: OptionType,
    @Enumerated(EnumType.STRING)
    val subOptionType: SubOptionType,
) : BaseEntity()
```

### Kafka Consumer (이벤트 핸들러 호출)

```kotlin
@Component
class OrderSagaEventListener(
    private val handler: OrderSagaEventHandler,
    private val processedEventRepository: ProcessedEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(topics = ["carry.Dispatch.events"], groupId = "order-group")
    fun onDispatchEvent(record: ConsumerRecord<String, String>) {
        // 1. 중복 체크 (ProcessedEvent)
        // 2. eventType 헤더로 분기
        // 3. handler 호출
    }

    @KafkaListener(topics = ["carry.Payment.events"], groupId = "order-group")
    fun onPaymentEvent(record: ConsumerRecord<String, String>) { ... }

    @KafkaListener(topics = ["carry.Delivery.events"], groupId = "order-group")
    fun onDeliveryEvent(record: ConsumerRecord<String, String>) { ... }
}
```

### REST Controller

```kotlin
@RestController
@RequestMapping("/api/v2/orders")
class OrderController(
    private val orderCommandUseCase: OrderCommandUseCase,
    private val orderQueryUseCase: OrderQueryUseCase,
) {
    @PostMapping                     // 주문 생성
    @GetMapping("/{orderId}")        // 주문 상세 조회
    @GetMapping("/my")               // 내 주문 목록
    @PostMapping("/{orderId}/cancel") // 주문 취소
}
```

---

## DB 스키마

```sql
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    laundromat_id   BIGINT NOT NULL,
    laundry_item_type VARCHAR(30) NOT NULL,
    -- 배송지 스냅샷
    road_address    VARCHAR(255) NOT NULL,
    detail_address  VARCHAR(255) NOT NULL,
    zip_code        VARCHAR(10),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    recipient_name  VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    entrance_info   VARCHAR(255),
    -- 일정
    desired_pickup_at  TIMESTAMPTZ NOT NULL,
    desired_delivery_at TIMESTAMPTZ NOT NULL,
    -- 사가 진행 중 기록
    carrier_id      BIGINT,
    invoice_id      BIGINT,
    total_amount    BIGINT,
    actual_weight   NUMERIC(10,2),
    cancel_reason   VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    -- 감사
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_selected_options (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    option_type     VARCHAR(30) NOT NULL,
    sub_option_type VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_options_order_id ON order_selected_options(order_id);
```

---

## 테스트 전략

| 계층 | 테스트 | 도구 |
|------|--------|------|
| Domain | OrderStatus 상태 전이, 취소 가능 여부, 팩토리 메서드 | JUnit 5, AssertJ |
| Application | OrderCommandService, OrderSagaEventHandler | MockK |
| Architecture | 헥사고날 의존성 규칙 | ArchUnit |
