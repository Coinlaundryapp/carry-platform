# 08. 테스트 전략

> 최종 수정일: 2026-03-11 (초안) · 상태 갱신: 2026-09-18
> 상태: **혼합 — 단위~사가 통합은 구현됨, "분리 후" MSA 테스트 절은 미채택**

> ⚠️ **상태가 갈린다:**
> - **실제 구현된 전략**: 순수 도메인 단위 테스트, ArchUnit 경계 강제, Testcontainers 기반 사가 통합 테스트,
>   **크로스모듈 계약 테스트는 #93에서 `testFixtures`의 `Fake<Port>` 방식(모놀리스 내)으로 구현** —
>   아래 "Pact/Spring Cloud Contract(분리 후)"가 아니라 이 형태다. 멱등성·동시성·Outbox 원자성(#123) 회귀 가드 포함.
> - **장애 주입은 Toxiproxy(Testcontainers, 인프로세스)로 채택됨** — 아래 "장애 주입 테스트" 절.
>   K8s Chaos Mesh 절(분리 후)과는 다른 계층이며, 그쪽은 여전히 미채택이다.
> - **아래 "마이크로서비스 분리 후" 절들(API 호환성·Chaos Mesh·카나리 Smoke·Linkerd)은 미채택**(ADR-0007).
>   분리를 안 하므로 해당 계층은 도입하지 않는다. 구상으로만 보존.

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

## 장애 주입 테스트 (Chaos Injection) — 구현됨

### 대상

- **DB 경로 단절/지연** — 커넥션 풀과 드라이버 타임아웃이 실제로 요청을 풀어주는지 (`DatasourceOutageChaosTest`)
- Kafka·PG 경로 주입은 **아직 하지 않았다**

### 도구

- Testcontainers + **Toxiproxy**(`ghcr.io/shopify/toxiproxy:2.5.0`) — DataSource 를 프록시 경유로 붙이고
  toxic(`timeout(0)` = 연결을 끊지 않고 데이터만 흘리지 않는 블랙홀)을 붙였다 뗀다.
  `connection-refused` 로 즉시 실패시키면 타임아웃 경로를 태울 수 없어 블랙홀을 쓴다.
- 전용 베이스 `ChaosTestBase` — 기존 `IntegrationTestBase`(컨테이너 직결)와 **분리**한다.
  프록시 홉과 컨텍스트 분기를 기존 통합 테스트 전체에 얹지 않기 위해서다.

### 단언하는 것 — 성능이 아니라 거동

절대 처리량·지연은 측정하지 않는다. 단언은 세 가지뿐이다.

1. 단절 시 **유한 시간 안에 실패**한다 (무한 대기하지 않는다)
2. 단절이 해소되면 **재기동·풀 재생성 없이 회복**한다
3. 단절·회복을 겪어도 **풀이 상한(20)을 넘겨 팽창하지 않는다**

3번이 핵심이다. 쿼리 실패를 커넥션 장애로 오인해 풀을 재생성하는 구조는 실패가 반복될 때
커넥션·스레드가 선형으로 증가해 프로세스를 죽인다.

> `application-test.yml` 이 HikariCP 설정을 덮지 않으므로 이 테스트가 관찰하는 거동은 **운영 설정의 거동**이다.
> 테스트가 `maximumPoolSize == 20` 을 직접 단언해 그 전제를 고정한다.

### 이 테스트가 처음 잡은 결함 (2026-09-04)

붙이자마자 1번이 RED 였다 — 끊긴 DB 로의 `SELECT 1` 이 **13,424,276ms(3시간 43분) 동안 반환되지 않았다.**

- `spring.datasource.hikari.connection-timeout: 3000` 은 커넥션 **획득**에만 적용된다. 이미 획득한 커넥션의
  소켓 읽기는 드라이버 소관이고, pgjdbc `socketTimeout` 기본값이 **0(무한)** 이다.
- 가상 스레드라 스레드 고갈은 늦게 오지만, 커넥션 20개가 모두 이 상태가 되면 서비스가 멈춘다.
  `leak-detection-threshold: 5000` 은 경고만 남길 뿐 스레드를 풀어주지 않는다.
- 조치: JDBC URL 이 프로파일별 환경변수라 `hikari.data-source-properties` 로 준다 —
  `socketTimeout: 10`(초) + `tcpKeepAlive: true`. 전 프로파일·테스트에 공통 적용된다.
- 적용 후 **11.2초**에 실패(GREEN). 이 테스트가 회귀 가드로 상시 실행된다.

**남은 과제**: DB 측 `statement_timeout` 병행, 10초를 넘겨야 하는 배치가 생기면 전용 데이터소스 분리,
Kafka·PG 경로 주입.

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
        // PostGIS 포함 이미지 — 인근 검색(ST_Distance·ST_DWithin)·V3 GIST 인덱스가 PostGIS에 의존.
        // 확장은 Flyway V0(CREATE EXTENSION postgis)가 생성한다. 로컬 docker도 동일 이미지.
        val postgres = PostgreSQLContainer("postgis/postgis:16-3.4")
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
