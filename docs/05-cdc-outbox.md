# 05. Transactional Outbox + Debezium CDC

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 왜 Outbox인가

도메인 상태 변경과 이벤트 발행을 **하나의 DB 트랜잭션**으로 묶어야 한다.
Kafka에 직접 발행하면 dual-write 문제가 발생한다 (DB는 커밋됐는데 Kafka 발행 실패, 또는 그 반대).

Outbox 패턴은 이벤트를 DB 테이블에 저장하고, Debezium이 WAL을 감시하여 Kafka로 발행한다.
애플리케이션은 DB 트랜잭션만 신경 쓰면 되고, 이벤트 발행의 신뢰성은 Debezium이 보장한다.

---

## Outbox 테이블 구조 (모듈별)

```sql
CREATE TABLE order_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,    -- 'Order'
    aggregate_id    VARCHAR(100) NOT NULL,    -- 주문 ID
    event_type      VARCHAR(100) NOT NULL,    -- 'OrderCreated'
    payload         JSONB NOT NULL,           -- 이벤트 데이터
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

각 모듈은 자체 Outbox 테이블을 소유한다: `order_outbox`, `payment_outbox`, `dispatch_outbox`.

---

## 전체 흐름

```
1. Application Service:
   @Transactional 내에서
   ├── 도메인 엔티티 저장 (JPA)
   └── Outbox 테이블에 이벤트 row INSERT
       (같은 트랜잭션 — 원자성 보장)

2. Debezium:
   ├── PostgreSQL WAL(논리적 복제)을 감시
   ├── *_outbox 테이블의 INSERT를 감지
   ├── Outbox Event Router SMT로 변환
   └── Kafka 토픽으로 발행
       토픽명: carry.order.events (aggregate_type 기반 라우팅)

3. Consumer (다른 모듈):
   ├── Kafka 토픽을 구독
   ├── 이벤트 처리 (멱등성 보장 필수)
   └── 처리된 이벤트 ID 기록 (중복 방지)
```

### 시퀀스 다이어그램

```
Application    PostgreSQL     Debezium      Kafka         Consumer
    │               │             │           │              │
    │── BEGIN TX ──→│             │           │              │
    │── INSERT ────→│ (entity)    │           │              │
    │── INSERT ────→│ (outbox)    │           │              │
    │── COMMIT ────→│             │           │              │
    │               │             │           │              │
    │               │── WAL ─────→│           │              │
    │               │             │── publish→│              │
    │               │             │           │── deliver ──→│
    │               │             │           │              │── process
```

---

## 애플리케이션 코드 예시

### Outbox 엔티티

```kotlin
@Entity
@Table(name = "order_outbox")
class OutboxEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val aggregateType: String,

    @Column(nullable = false)
    val aggregateId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
```

### Application Service에서 Outbox 저장

```kotlin
@Service
class OrderCreationService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun createOrder(command: CreateOrderCommand): Order {
        // 1. 도메인 엔티티 저장
        val order = Order.create(command)
        orderRepository.save(order)

        // 2. 같은 트랜잭션에서 Outbox 이벤트 저장
        val event = OrderCreatedEvent(
            orderId = order.id,
            customerId = command.customerId,
            totalAmount = order.totalAmount
        )
        outboxRepository.save(
            OutboxEvent(
                aggregateType = "Order",
                aggregateId = order.id.toString(),
                eventType = "OrderCreated",
                payload = objectMapper.writeValueAsString(event)
            )
        )

        return order
    }
}
```

---

## Debezium Connector 설정

```json
{
  "name": "carry-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "carry",
    "database.password": "${POSTGRES_PASSWORD}",
    "database.dbname": "carry",
    "plugin.name": "pgoutput",
    "publication.autocreate.mode": "filtered",
    "table.include.list": "public.order_outbox,public.payment_outbox,public.dispatch_outbox",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.topic.replacement": "carry.${routedByValue}.events",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType",

    "tombstones.on.delete": false,
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": false
  }
}
```

### Outbox Event Router 동작

Debezium의 Outbox Event Router SMT는 outbox 테이블의 row를 다음과 같이 라우팅한다:

| Outbox Column | Kafka 매핑 |
|---------------|-----------|
| `aggregate_id` | Message Key (파티션 키 → 순서 보장) |
| `aggregate_type` | Topic name (`carry.Order.events`) |
| `event_type` | Header (`OrderCreated`) |
| `payload` | Message Value |
| `id` | Header (중복 제거용) |

---

## 이벤트 계약 (carry-event 모듈)

```kotlin
// carry-event/src/main/kotlin/com/carry/event/DomainEvent.kt
data class DomainEvent<T>(
    val eventId: String,          // UUID
    val eventType: String,        // "OrderCreated"
    val aggregateId: String,      // 주문 ID
    val aggregateType: String,    // "Order"
    val payload: T,
    val occurredAt: Instant,
    val traceId: String?          // OTel trace context 전파
)

// carry-event/src/main/kotlin/com/carry/event/order/OrderCreatedEvent.kt
data class OrderCreatedEvent(
    val orderId: Long,
    val customerId: Long,
    val laundromatId: Long,
    val totalAmount: Long,
    val items: List<OrderItemSummary>
)
```

---

## 멱등성 보장

Kafka는 at-least-once delivery를 보장하므로, Consumer는 중복 이벤트를 안전하게 처리해야 한다.

```kotlin
@KafkaListener(topics = ["carry.Order.events"])
fun handleOrderEvent(record: ConsumerRecord<String, String>) {
    val eventId = record.headers().lastHeader("id")?.let { String(it.value()) }
        ?: return

    // 이미 처리된 이벤트면 스킵
    if (processedEventRepository.existsById(eventId)) return

    val payload = objectMapper.readValue(record.value(), OrderCreatedEvent::class.java)

    // 비즈니스 로직 처리
    paymentService.createPaymentRecord(payload)

    // 처리 완료 기록
    processedEventRepository.save(ProcessedEvent(id = eventId, processedAt = Instant.now()))
}
```

---

## Outbox 테이블 정리 (Housekeeping)

Outbox 테이블은 계속 증가하므로, 주기적으로 오래된 row를 삭제해야 한다.
Debezium이 이미 발행한 이벤트는 Kafka에 보존되므로 DB에서는 안전하게 삭제할 수 있다.

```sql
-- 7일 이상 된 outbox 이벤트 삭제 (스케줄링)
DELETE FROM order_outbox WHERE created_at < now() - INTERVAL '7 days';
```

이를 Spring `@Scheduled`로 자동화하거나, PostgreSQL의 `pg_cron` 확장을 사용할 수 있다.
