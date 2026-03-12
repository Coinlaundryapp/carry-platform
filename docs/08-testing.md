# 08. 테스트 전략

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 테스트 피라미드

```
                    ┌───────────┐
                    │   E2E     │  Saga 전체 흐름, 카나리 검증
                   ─┤  (5%)    ├─
                  / └───────────┘ \
                 /  ┌───────────┐  \
                ─── │ Contract  │ ───  이벤트 스키마, API 호환성
               /    │  (5%)    │    \
              /     └───────────┘     \
             /      ┌───────────┐      \
            ─────── │ArchUnit   │ ───────  모듈 경계, 의존성 규칙
           /        │  (5%)    │        \
          /         └───────────┘         \
         /          ┌───────────┐          \
        ──────────  │Integration│  ──────────  JPA, Kafka, 모듈 내 흐름
       /            │  (25%)   │            \
      /             └───────────┘             \
     /              ┌───────────┐              \
    ────────────────│   Unit    │────────────────  도메인 모델, 서비스
                    │  (60%)   │
                    └───────────┘
```

---

## Unit Test (60%)

### 대상
- 도메인 모델 (Aggregate Root, Entity, Value Object)
- 도메인 서비스 (비즈니스 규칙)
- 상태 전이 로직

### 도구
- JUnit 5
- MockK (Kotlin-native mocking)

### 예시: 주문 상태 전이

```kotlin
class OrderStatusTest {

    @Test
    fun `CREATED에서 PAYMENT_PENDING으로 전이 가능`() {
        val order = Order.create(/* ... */)
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)

        order.markPaymentPending()
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
    }

    @Test
    fun `DELIVERED에서 CANCELLED로 전이 불가`() {
        val order = createOrderWithStatus(OrderStatus.DELIVERED)

        assertThrows<IllegalStateException> {
            order.cancel()
        }
    }
}
```

### 예시: 가격 계산

```kotlin
class PriceCalculationServiceTest {

    @Test
    fun `일반 세탁 + 뜨거운 물 옵션 가격 계산`() {
        val result = priceService.calculate(
            laundryType = LaundryItemType.REGULAR,
            washOption = WashOption.HOT_WATER,
            weight = 5.0
        )
        assertThat(result.totalAmount).isEqualTo(15_000)
    }
}
```

---

## Integration Test (25%)

### 대상
- JPA Repository (실제 DB 연동)
- Kafka Consumer/Producer (실제 브로커)
- Outbox 저장 → 이벤트 발행 흐름

### 도구
- Testcontainers (PostgreSQL, Kafka)
- Spring Boot Test (`@SpringBootTest`)
- `@DataJpaTest` (Repository 단위)

### 예시: Outbox 통합 테스트

```kotlin
@SpringBootTest
@Testcontainers
class OrderOutboxIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16")

        @Container
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
    }

    @Autowired lateinit var orderService: OrderCreationService
    @Autowired lateinit var outboxRepository: OutboxEventRepository

    @Test
    fun `주문 생성 시 Outbox에 이벤트가 저장된다`() {
        val command = CreateOrderCommand(/* ... */)

        orderService.createOrder(command)

        val outboxEvents = outboxRepository.findAll()
        assertThat(outboxEvents).hasSize(1)
        assertThat(outboxEvents[0].eventType).isEqualTo("OrderCreated")
        assertThat(outboxEvents[0].aggregateType).isEqualTo("Order")
    }
}
```

### 예시: Kafka Consumer 통합 테스트

```kotlin
@SpringBootTest
@Testcontainers
@EmbeddedKafka(topics = ["carry.Order.events"])
class PaymentEventConsumerTest {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Autowired lateinit var paymentRepository: PaymentRepository

    @Test
    fun `OrderCreatedEvent 수신 시 결제 레코드가 생성된다`() {
        val event = """{"orderId": 1, "customerId": 100, "totalAmount": 15000}"""

        kafkaTemplate.send("carry.Order.events", "1", event).get()

        // 비동기 처리 대기
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val payment = paymentRepository.findByOrderId(1L)
            assertThat(payment).isNotNull
            assertThat(payment!!.status).isEqualTo(PaymentStatus.PENDING)
        }
    }
}
```

---

## Architecture Test (5%)

### ArchUnit — 모듈 경계 강제

```kotlin
@AnalyzeClasses(packages = ["com.carry"])
class ModuleBoundaryTest {

    @Test
    fun `order 모듈은 다른 도메인 모듈을 직접 import하지 않는다`() {
        noClasses()
            .that().resideInAPackage("com.carry.order..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.carry.payment..",
                "com.carry.dispatch..",
                "com.carry.user.domain..",
                "com.carry.laundromat.domain.."
            )
            .check(importedClasses)
    }

    @Test
    fun `domain 레이어는 infrastructure를 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `presentation은 domain을 직접 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.repository..")
            .check(importedClasses)
    }
}
```

---

## Contract Test (5%)

### 이벤트 스키마 호환성 테스트

모듈 간 이벤트 스키마가 변경될 때, 소비자가 깨지지 않는지 검증한다.

```kotlin
class OrderEventContractTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `OrderCreatedEvent는 필수 필드를 포함해야 한다`() {
        val json = """
        {
            "orderId": 1,
            "customerId": 100,
            "laundromatId": 10,
            "totalAmount": 15000,
            "items": []
        }
        """.trimIndent()

        val event = objectMapper.readValue(json, OrderCreatedEvent::class.java)

        assertThat(event.orderId).isEqualTo(1)
        assertThat(event.customerId).isEqualTo(100)
        assertThat(event.totalAmount).isEqualTo(15000)
    }

    @Test
    fun `OrderCreatedEvent에 새 필드 추가 시 기존 소비자가 깨지지 않는다`() {
        // 새 필드 "priority"가 추가된 JSON
        val json = """
        {
            "orderId": 1,
            "customerId": 100,
            "laundromatId": 10,
            "totalAmount": 15000,
            "items": [],
            "priority": "HIGH"
        }
        """.trimIndent()

        // 기존 data class로 역직렬화 — 알 수 없는 필드 무시
        val event = objectMapper
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(json, OrderCreatedEvent::class.java)

        assertThat(event.orderId).isEqualTo(1)
    }
}
```

### API 호환성 (마이크로서비스 분리 후)

서비스 분리 시 REST API 호환성을 Spring Cloud Contract 또는 Pact로 검증한다:

```
Producer (carry-laundromat):
  - API 명세를 Contract으로 정의
  - 빌드 시 Contract 테스트 실행 → Stub 생성

Consumer (carry-order):
  - Producer의 Stub을 사용하여 Adapter 테스트
  - 실제 HTTP 호출 없이 API 호환성 검증
```

---

## E2E Test — Saga 전체 흐름 (5%)

### 대상
- 주문 → 결제 → 배차 Saga 전체 흐름
- 보상 트랜잭션 (결제 실패 시 주문 취소)

### 도구
- Testcontainers (PostgreSQL, Kafka, Debezium)
- REST Assured (API 호출)

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderSagaE2ETest {

    @Test
    fun `주문 생성 → 결제 완료 → 배차 생성 전체 흐름`() {
        // 1. 주문 생성 API 호출
        val orderId = given()
            .body(CreateOrderRequest(/* ... */))
            .post("/api/v2/orders")
            .then().statusCode(201)
            .extract().path<Long>("data.orderId")

        // 2. 결제 완료 이벤트가 발행될 때까지 대기
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val order = orderRepository.findById(orderId)
            assertThat(order?.status).isEqualTo(OrderStatus.PAID)
        }

        // 3. 배차가 생성될 때까지 대기
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val dispatch = dispatchRepository.findByOrderId(orderId)
            assertThat(dispatch).isNotNull
            assertThat(dispatch!!.status).isEqualTo(DispatchStatus.WAITING)
        }
    }

    @Test
    fun `결제 실패 시 주문이 자동 취소된다`() {
        // 1. 잘못된 결제 정보로 주문 생성
        val orderId = createOrderWithInvalidPayment()

        // 2. 주문이 PAYMENT_FAILED로 변경되는지 확인
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val order = orderRepository.findById(orderId)
            assertThat(order?.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }
    }
}
```

---

## 마이크로서비스 테스트 전략 (분리 후)

모놀리스에서 마이크로서비스로 전환 시, 테스트 계층이 확장된다:

### 추가되는 테스트

| 테스트 유형 | 목적 | 도구 |
|------------|------|------|
| **Consumer-Driven Contract** | API 호환성 보장 | Pact / Spring Cloud Contract |
| **Service Integration** | 서비스 간 통신 검증 | WireMock + Testcontainers |
| **Chaos Engineering** | 장애 복원력 검증 | Chaos Mesh (K8s) |
| **Load Test** | 성능/확장성 검증 | k6 / Gatling |
| **Canary Smoke Test** | 배포 직후 핵심 API 검증 | Flagger webhooks |

### Consumer-Driven Contract (서비스 분리 후)

```
┌─────────────────┐                    ┌──────────────────┐
│ carry-order     │                    │ carry-laundromat │
│ (Consumer)      │                    │ (Provider)       │
│                 │                    │                  │
│ Pact 파일 생성  │──── Contract ─────→│ Provider 검증    │
│ (기대하는 API)   │    (Pact Broker)   │ (실제 API 대조)  │
└─────────────────┘                    └──────────────────┘
```

### Chaos Engineering 시나리오 (K8s 환경)

```yaml
# Chaos Mesh: Payment 서비스 네트워크 지연 주입
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: payment-delay
spec:
  action: delay
  mode: all
  selector:
    labelSelectors:
      app: carry-payment
  delay:
    latency: "3s"
  duration: "5m"
```

검증 포인트:
- Payment 지연 시 Order 모듈의 타임아웃 처리가 동작하는가?
- Saga 보상 트랜잭션이 올바르게 실행되는가?
- Linkerd의 리트라이 정책이 적용되는가?

### 카나리 배포 Smoke Test

Flagger의 `pre-rollout` webhook으로 핵심 API를 검증한 후 트래픽을 전환한다:

```yaml
webhooks:
  - name: smoke-test
    type: pre-rollout
    url: http://flagger-loadtester.carry/
    metadata:
      type: bash
      cmd: |
        curl -sf http://carry-order-canary:8080/actuator/health &&
        curl -sf http://carry-order-canary:8080/api/v2/orders/health-check
```

---

## 테스트 인프라

### Testcontainers 공통 설정

```kotlin
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("carry_test")

        @Container
        @JvmStatic
        val kafka = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
        )

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}
```

모든 통합 테스트는 이 베이스 클래스를 상속하여 컨테이너를 공유한다.
