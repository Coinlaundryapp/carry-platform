# 성능 회귀 테스트 2-tier 구현 계획

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주요 API의 성능 회귀를 두 축으로 자동 검출한다 — (Tier1) 결정적 SQL 쿼리-카운트 회귀 가드를 CI 게이트로, (Tier2) Gatling 부하·p95 측정을 온디맨드로.

**Architecture:** Tier1은 `datasource-proxy`로 test DataSource를 래핑해 `QueryCountHolder`로 SELECT/INSERT 횟수를 단언하는 Testcontainers IT(기존 `IntegrationTestBase` 상속)다. 머신 독립적이라 매 PR CI에서 안정 게이트로 동작한다. Tier2는 격리된 `carry-loadtest` Gradle 모듈에서 Gatling Java DSL로 로컬 docker 풀스택을 외부 부하하여 p50/p95/p99를 측정한다. 프로덕션 코드는 변경하지 않는다(Gatling이 공유 secret으로 JWT를 직접 생성).

**Tech Stack:** Kotlin 2.1.0, Spring Boot 3.4.1, Gradle 8.12.1 (JDK 21 toolchain), Testcontainers 1.20.4, `net.ttddyy:datasource-proxy:1.10.1`, `io.gatling.gradle:3.13.5`, `com.auth0:java-jwt`.

**참고:**
- spec: `docs/superpowers/specs/2026-06-09-performance-regression-tests-design.md`
- 이슈 #94, 브랜치 `feature/performance-regression-tests`
- 기존 IT 패턴 레퍼런스: `carry-app/src/test/kotlin/com/carry/app/concurrency/ConcurrencyIntegrationTest.kt`
- 커밋 규약: 한글 본문 + 영어 conventional prefix + `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 빌드 prefix: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7"` (미커밋 `gradle.properties`가 `org.gradle.java.home` 지정). 테스트 통과 검증은 `build/test-results/**/*.xml`의 tests/failures 수로 확인(BUILD SUCCESSFUL만으로 판단 금지).

---

## File Structure

**Tier1 (carry-app test 소스셋):**
- Create: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountTestConfig.kt` — test DataSource를 ProxyDataSource로 래핑하는 `BeanPostProcessor`
- Create: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt` — 3개 시나리오 쿼리-카운트 가드 IT
- Modify: `carry-app/build.gradle.kts` — `datasource-proxy` testImplementation 추가

**Tier2 (신규 격리 모듈):**
- Modify: `settings.gradle.kts` — `include("carry-loadtest")`
- Create: `carry-loadtest/build.gradle.kts` — gatling plugin + java-jwt
- Create: `carry-loadtest/src/gatling/java/com/carry/loadtest/TokenFactory.java` — 공유 secret으로 access token 생성
- Create: `carry-loadtest/src/gatling/java/com/carry/loadtest/CarryLoadSimulation.java` — 3개 시나리오 + assertions
- Create: `carry-loadtest/src/gatling/resources/seed.sql` — 부하용 시드 데이터
- Create: `carry-loadtest/README.md` — 실행 가이드 + p95 기준선 기록

---

## Chunk 1: Tier1 — 결정적 쿼리 카운트 회귀 가드

### Task 1: datasource-proxy 의존성 + QueryCountTestConfig

**Files:**
- Modify: `carry-app/build.gradle.kts` (testImplementation 블록, 현재 38-48행)
- Create: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountTestConfig.kt`

- [ ] **Step 1: 의존성 추가**

`carry-app/build.gradle.kts`의 testImplementation 블록(line 46, `assertj-core` 다음)에 추가:

```kotlin
    testImplementation("net.ttddyy:datasource-proxy:1.10.1")
```

- [ ] **Step 2: 빌드로 의존성 해소 확인**

Run: `./gradlew :carry-app:dependencies --configuration testRuntimeClasspath` (또는 다음 컴파일 시 해소). datasource-proxy가 클래스패스에 보이면 OK.

- [ ] **Step 3: QueryCountTestConfig 작성**

ProxyDataSource는 실제 DataSource를 감싸 모든 JDBC 쿼리를 `QueryCountHolder`(ThreadLocal)에 집계한다. `@TestConfiguration`으로 두고 가드 테스트에서만 `@Import`한다 — 다른 IT에는 영향 없음.

```kotlin
package com.carry.app.performance

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import javax.sql.DataSource

/**
 * 테스트 전용. 실제 DataSource를 datasource-proxy로 래핑해 실행 쿼리 수를 집계한다.
 * 프로덕션 DataSource 구성은 건드리지 않으며, 이 설정을 @Import 한 테스트에만 적용된다.
 */
@TestConfiguration
class QueryCountTestConfig {

    @Bean
    fun queryCountProxyPostProcessor(): BeanPostProcessor = object : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean is DataSource && bean !is net.ttddyy.dsproxy.support.ProxyDataSource) {
                return ProxyDataSourceBuilder.create(bean)
                    .name("perf-guard")
                    .countQuery() // DataSourceQueryCountListener 등록 → QueryCountHolder 집계
                    .build()
            }
            return bean
        }
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :carry-app:compileTestKotlin`
Expected: BUILD SUCCESSFUL (새 파일 컴파일 통과)

- [ ] **Step 5: 커밋**

```bash
git add carry-app/build.gradle.kts carry-app/src/test/kotlin/com/carry/app/performance/QueryCountTestConfig.kt
git commit -F- <<'EOF'
test(perf): datasource-proxy 쿼리 카운트 측정 설정 추가 (#94)

테스트 전용 BeanPostProcessor로 DataSource를 ProxyDataSource로 래핑.
프로덕션 DataSource 무변경, @Import한 테스트에만 적용.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 2: 커서 목록 조회 N+1 가드 (핵심)

**핵심 가드.** 결과 건수를 10→20으로 늘려도 SELECT 수가 동일함(= N+1 부재)을 단언한다. 비례 증가하면 N+1 회귀다.

**Files:**
- Create: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt`

레퍼런스: `ConcurrencyIntegrationTest`의 시드/주입/truncate 패턴을 그대로 따른다. 목록 조회 use case는 `OrderQueryUseCase.getOrdersByCustomer(userId, cursor, size)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.carry.app.performance

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import net.ttddyy.dsproxy.QueryCountHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(SagaIntegrationTestConfig::class, QueryCountTestConfig::class)
class QueryCountGuardTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var orderQueryUseCase: OrderQueryUseCase
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() = TestFixtures.truncateAll(jdbc)

    private fun seedOrders(count: Int) {
        repeat(count) {
            orderCommandService.createOrder(
                CreateOrderCommand(
                    customerId = TestFixtures.CUSTOMER_ID,
                    shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                    laundromatId = TestFixtures.LAUNDROMAT_ID,
                    laundryItemType = "NORMAL",
                    selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                    desiredPickupAt = TestFixtures.desiredPickupAt(),
                    desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
                ),
            )
        }
    }

    /** 조회 1회의 SELECT 수를 잰다(생성 쿼리는 clear로 제외). */
    private fun selectsForListQuery(size: Int): Int {
        QueryCountHolder.clear()
        orderQueryUseCase.getOrdersByCustomer(TestFixtures.CUSTOMER_ID, null, size)
        return QueryCountHolder.getGrandTotal().select.toInt()
    }

    @Test
    fun `주문 목록 조회는 결과 건수에 비례해 쿼리가 늘지 않는다 (N+1 부재)`() {
        seedOrders(10)
        val selects10 = selectsForListQuery(50)

        TestFixtures.truncateAll(jdbc)
        setUp()
        seedOrders(20)
        val selects20 = selectsForListQuery(50)

        // N+1이 없으면 건수가 2배여도 SELECT 수는 동일
        assertThat(selects20).isEqualTo(selects10)
        // 고정 상한: 목록 1쿼리(+필요시 연관 1~2). 근거 = 실측 후 기록.
        assertThat(selects10).isLessThanOrEqualTo(3)
    }
}
```

- [ ] **Step 2: 테스트 실행 → RED 확인 (현재 N+1 실재)**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "com.carry.app.performance.QueryCountGuardTest"`
Expected: **FAIL.** `OrderJpaEntity.selectedOptions`는 `@OneToMany(fetch = EAGER)`이고 목록 쿼리(`findByCustomerIdWithCursor`)에 fetch-join/`@EntityGraph`/batch가 없으며 `open-in-view: false`라, 주문마다 컬렉션 초기화 SELECT가 1회씩 추가 발생한다(= N+1). 따라서 `selects10 ≈ 1+10`, `selects20 ≈ 1+20`이 되어 `selects20 == selects10`과 `selects10 <= 3`이 모두 깨진다. **이 RED가 곧 성능 회귀 테스트가 발견한 실제 결함이다.** 실측한 selects10/selects20 값을 기록.

- [ ] **Step 3: N+1 수정 — hibernate batch fetch (정당한 프로덕션 설정 변경)**

fetch-join은 cursor 페이지네이션(LIMIT)과 충돌(in-memory 페이징)하므로, EAGER 컬렉션을 IN-배치로 한 번에 로딩하는 `default_batch_fetch_size`를 설정한다. 다음 3개 파일의 `spring.jpa.properties.hibernate` 블록에 추가:

- `carry-app/src/main/resources/application.yml`
- `carry-app/src/main/resources/application-local.yml`
- `carry-app/src/test/resources/application-test.yml`

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

(각 파일의 기존 `hibernate:` 하위에 키를 추가. 이미 `dialect`/`format_sql` 등이 있는 블록에 합친다.) 이로써 K개 주문의 selectedOptions를 1개의 IN 쿼리로 배치 로딩 → 목록 SELECT 수가 건수와 무관하게 고정(목록 1 + 배치 1 ≈ 2).

- [ ] **Step 4: 재실행 → GREEN + 상한 실측 기록**

Run: 위와 동일.
Expected: PASS. `selects20 == selects10` 성립, `selects10`이 작은 고정값(≈2). 실측치에 맞춰 상한(`<= 3`)을 확인/조정하고 주석에 근거 기록(예: `// 실측 2026-06-09: 목록 1 + selectedOptions 배치 1 = 2 SELECT`). `build/test-results/test/*.xml`로 failures=0 확인.

- [ ] **Step 5: 커밋 (가드 + N+1 수정 함께)**

```bash
git add carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt carry-app/src/main/resources/application.yml carry-app/src/main/resources/application-local.yml carry-app/src/test/resources/application-test.yml
git commit -F- <<'EOF'
fix(order): 주문 목록 조회 N+1 제거 + 회귀 가드 (#94)

성능 회귀 가드가 selectedOptions EAGER N+1을 검출.
fetch-join은 cursor 페이지네이션과 충돌하므로 default_batch_fetch_size=100으로
IN-배치 로딩 적용 → 건수 무관 고정 쿼리. 결과 건수 10→20에도 SELECT 불변을 단언.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 3: 주문 생성 쿼리 카운트 상한 가드

**Files:**
- Modify: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt`

- [ ] **Step 1: 테스트 추가**

```kotlin
    @Test
    fun `주문 생성은 write 경로 쿼리 수가 상한을 넘지 않는다`() {
        QueryCountHolder.clear()
        orderCommandService.createOrder(
            CreateOrderCommand(
                customerId = TestFixtures.CUSTOMER_ID,
                shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                laundromatId = TestFixtures.LAUNDROMAT_ID,
                laundryItemType = "NORMAL",
                selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                desiredPickupAt = TestFixtures.desiredPickupAt(),
                desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
            ),
        )
        val qc = QueryCountHolder.getGrandTotal()
        // 주문 생성 = 검증 SELECT + order/option INSERT + outbox INSERT.
        // 상한은 실측 + 여유. 근거 주석으로 기록. (qc.total은 Long → 리터럴에 L)
        assertThat(qc.total).isLessThanOrEqualTo(15L)
    }
```

- [ ] **Step 2: 실행해 실측 후 상한 조정**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "com.carry.app.performance.QueryCountGuardTest"`
Expected: 실측 total을 확인하고 상한을 실측+여유로 설정. 주석에 실측치 기록. PASS.

- [ ] **Step 3: 커밋**

```bash
git add carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt
git commit -F- <<'EOF'
test(perf): 주문 생성 쿼리 수 상한 가드 (#94)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 4: 배차 선점(수락) 쿼리 카운트 상한 가드

배차의 carrier 수락 주 경로는 공개 배차 선점(`claimDispatch`)이다. 선행 상태(PENDING dispatch)는 `ConcurrencyIntegrationTest.createPendingDispatch()`와 동일하게 주문 생성 → OutboxEvent를 saga handler로 흘려 만든다.

**Files:**
- Modify: `carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt`

- [ ] **Step 1a: 주입 필드 4개 추가**

클래스 상단에 추가(모두 `ConcurrencyIntegrationTest`에 존재 확인됨):

```kotlin
    @Autowired lateinit var dispatchCommandService: com.carry.dispatch.application.service.DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: com.carry.dispatch.application.port.outbound.DispatchPersistencePort
    @Autowired lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper
```

- [ ] **Step 1b: 시드 보강 + createPendingDispatch 헬퍼 복제**

`@BeforeEach`에 `TestFixtures.insertCarrier(jdbc)` + `TestFixtures.insertCarrierArea(jdbc)` 추가. `ConcurrencyIntegrationTest.createPendingDispatch()`(주문 생성 → outbox의 `OrderCreatedEvent`를 `dispatchSagaHandler.onOrderCreated`로 흘려 PENDING dispatch 생성 → id 반환)를 그대로 복제한다.

- [ ] **Step 1c: 테스트 추가**

```kotlin
    @Test
    fun `배차 선점은 쿼리 수가 상한을 넘지 않는다`() {
        val dispatchId = createPendingDispatch()
        QueryCountHolder.clear()
        dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, TestFixtures.CARRIER_ID))
        val qc = QueryCountHolder.getGrandTotal()
        assertThat(qc.total).isLessThanOrEqualTo(10L) // 실측 + 여유 (Long 리터럴)
    }
```

import 추가: `com.carry.dispatch.application.port.inbound.ClaimDispatchCommand`.

- [ ] **Step 2: 실행·실측·상한 조정**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "com.carry.app.performance.QueryCountGuardTest"`
Expected: 실측 후 상한 조정, 주석 기록, PASS.

- [ ] **Step 3: 커밋**

```bash
git add carry-app/src/test/kotlin/com/carry/app/performance/QueryCountGuardTest.kt
git commit -F- <<'EOF'
test(perf): 배차 선점 쿼리 수 상한 가드 (#94)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 5: teeth — 뮤테이션으로 가드 실효성 검증

가드가 회귀를 실제로 잡는지 확인한다. **커밋하지 않는다 — 확인 후 즉시 원복.**

- [ ] **Step 1: N+1 회귀 재유발**

Task 2에서 넣은 `default_batch_fetch_size`를 일시 제거(3개 yml 중 `application-test.yml`에서 키만 빼도 충분)하거나, 목록 조회 경로에서 각 주문마다 추가 SELECT가 나도록 일시 변경한다. 이는 Task 2 이전의 N+1 상태로 되돌리는 것과 같다.

- [ ] **Step 2: 가드가 RED 되는지 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "com.carry.app.performance.QueryCountGuardTest"`
Expected: `주문 목록 조회는 ... N+1 부재` 테스트가 FAIL (selects20 > selects10). 가드가 N+1을 잡음을 확인.

- [ ] **Step 3: 원복**

`git checkout -- <변경한 파일>` 로 일시 변경을 되돌린다. 재실행해 GREEN 확인. (이 task는 커밋 없음.)

### Task 6: Chunk 1 전체 회귀 검증

- [ ] **Step 1: carry-app 전체 테스트**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test`
Expected: 전체 GREEN. `build/test-results/test/*.xml`에서 failures=0, errors=0 확인(BUILD SUCCESSFUL만으로 판단 금지). 기존 Testcontainers IT가 새 BeanPostProcessor에 영향받지 않았는지(가드 테스트만 `@Import`하므로 영향 없어야 함) 확인.

---

## Chunk 2: Tier2 — Gatling 부하·p95 측정 (온디맨드)

### Task 7: carry-loadtest 모듈 스캐폴딩

**Files:**
- Modify: `settings.gradle.kts` (마지막 `include("carry-app")` 뒤)
- Create: `carry-loadtest/build.gradle.kts`

- [ ] **Step 1: settings 등록**

`settings.gradle.kts`의 Application 섹션 뒤에 추가:

```kotlin
// ── Load Test (격리, 일반 빌드 비참여) ──
include("carry-loadtest")
```

- [ ] **Step 2: 모듈 build.gradle.kts 작성**

루트 `subprojects` 블록이 모든 모듈에 kotlin/JDK21 toolchain을 적용하므로, gatling plugin과 java-jwt만 더한다. 이 모듈은 프로덕션 모듈을 의존하지 않는다(외부 HTTP 부하 + 자체 토큰 생성).

```kotlin
plugins {
    id("io.gatling.gradle") version "3.13.5"
}

dependencies {
    gatlingImplementation("com.auth0:java-jwt:4.4.0")
}
```

- [ ] **Step 3: plugin/Gradle 호환 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-loadtest:tasks`
Expected: `gatlingRun` task가 보임. plugin이 Gradle 8.12.1/JDK21에서 resolve 안 되면 README에 명시하고 직전 호환 버전으로 핀(예: 3.11.x). 호환 버전을 확정해 기록.

- [ ] **Step 4: 커밋**

```bash
git add settings.gradle.kts carry-loadtest/build.gradle.kts
git commit -F- <<'EOF'
test(perf): carry-loadtest 모듈 스캐폴딩 (Gatling, #94)

격리 모듈 — 일반 빌드/테스트 그래프 비참여. gatling plugin + java-jwt.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 8: 부하용 시드 SQL

부하 전 DB에 customer/carrier/laundromat/shippingAddress/serviceArea와 선점 대상 PENDING 배차 풀을 시드한다. `TestFixtures`의 INSERT 문과 동일 스키마를 SQL로 옮긴다.

**Files:**
- Create: `carry-loadtest/src/gatling/resources/seed.sql`

- [ ] **Step 1: seed.sql 작성**

`TestFixtures.kt`의 INSERT 컬럼/값을 그대로 SQL로 옮긴다. 최소:
- customer(id=1, role=CUSTOMER), carrier(id=2, role=CARRIER)
- laundromat(id=1), shipping_address(id=1, user_id=1)
- service_area(GANGNAM) + 7일 00:00~23:59 스케줄 (주문 생성의 영업시간 검증 통과용)
- carrier 2에 대한 `dispatch_carrier_areas`(area_code=GANGNAM) — available/claim 노출 조건
- claim 부하용 PENDING `dispatch_dispatches` 풀: 각 행은 선행 `orders` 행이 필요(`order_id` NOT NULL UNIQUE FK). 따라서 orders N개 + dispatch N개를 쌍으로 INSERT. dispatch 필수 컬럼: `order_id`, `laundromat_id`, `status='PENDING'`, `carrier_id=NULL`, `area_code='GANGNAM'`, `desired_pickup_at`(미래), `version=0`(낙관락 컬럼, V18 추가). 풀 크기 ≥ claim 시나리오 사용자 수(Task 10).

> 주의: claim은 PENDING→ACCEPTED로 행을 **소비**한다. 풀이 고갈되면 이후 claim은 실패(409)하므로, claim 사용자 수만큼 PENDING을 시드하거나 claim 부하를 작게 둔다(Task 10에서 일치시킴).

- [ ] **Step 2: 시드 적용 확인 (수동, 스키마 생성 이후)**

**중요한 순서**: `local` 프로파일은 `flyway.enabled: false` + `ddl-auto: update`라 **스키마는 bootRun이 기동 시 Hibernate로 생성**한다(Task 11). 따라서 seed.sql은 **bootRun으로 앱이 한 번 떠서 스키마가 만들어진 뒤** 적용해야 한다(Task 11 Step 순서 참조). 적용:
Run: `docker exec -i <postgres_container> psql -U <user> -d <db> < carry-loadtest/src/gatling/resources/seed.sql`
Expected: 에러 없이 INSERT. 컬럼 권위는 `TestFixtures.kt` + 각 모듈의 마이그레이션(예: `carry-dispatch/src/main/resources/db/migration/`, `carry-user/...`, `carry-order/...`) — `carry-app/.../db/migration`에는 outbox 테이블만 있으므로 도메인 컬럼 권위가 아니다.

- [ ] **Step 3: 커밋**

```bash
git add carry-loadtest/src/gatling/resources/seed.sql
git commit -F- <<'EOF'
test(perf): Gatling 부하용 시드 SQL (#94)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 9: 토큰 생성 유틸 (프로덕션 코드 0 변경)

Gatling이 서버와 동일한 `jwt.secret`(부하 실행 시 env로 주입)으로 `purpose=ACCESS`, `role`, `sub=userId` 클레임을 가진 HMAC256 JWT를 직접 만든다. `JwtProvider.createAccessToken`과 동일 클레임 구조(`JwtProvider.kt:18-26` 참조).

**Files:**
- Create: `carry-loadtest/src/gatling/java/com/carry/loadtest/TokenFactory.java`

- [ ] **Step 1: TokenFactory 작성**

```java
package com.carry.loadtest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.Date;

/** 서버와 동일한 secret/클레임으로 access 토큰을 생성한다(JwtProvider.createAccessToken 미러). */
public final class TokenFactory {
    private static final String SECRET =
        System.getenv().getOrDefault("JWT_SECRET", "test-secret-key-for-integration-tests");

    public static String accessToken(long userId, String role) {
        Algorithm alg = Algorithm.HMAC256(SECRET);
        return JWT.create()
            .withSubject(String.valueOf(userId))
            .withClaim("purpose", "ACCESS")
            .withClaim("role", role)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
            .sign(alg);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-loadtest:gatlingClasses`
Expected: 컴파일 통과.

- [ ] **Step 3: 커밋**

```bash
git add carry-loadtest/src/gatling/java/com/carry/loadtest/TokenFactory.java
git commit -F- <<'EOF'
test(perf): Gatling 토큰 생성 유틸 (#94)

서버 secret 공유로 access JWT 직접 생성 → 프로덕션 코드 0 변경.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 10: Gatling 시뮬레이션 + assertions

**Files:**
- Create: `carry-loadtest/src/gatling/java/com/carry/loadtest/CarryLoadSimulation.java`

3개 시나리오: ① 주문 목록 조회(`GET /api/v2/orders/my`, CUSTOMER 토큰) ② 주문 생성(`POST /api/v2/orders`, CUSTOMER 토큰) ③ 배차 목록/선점(`GET /api/v2/dispatches/available` → `POST /api/v2/dispatches/{id}/claim`, CARRIER 토큰). baseUrl은 `http://localhost:8080`.

- [ ] **Step 1: 시뮬레이션 작성 (Gatling Java DSL)**

핵심 골격(상세 body는 `CreateOrderRequest` 스키마에 맞춤 — `OrderControllerTest.kt:95-104` 참조):

```java
package com.carry.loadtest;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class CarryLoadSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json").contentTypeHeader("application/json");

    String customerToken = TokenFactory.accessToken(1L, "CUSTOMER");
    String carrierToken  = TokenFactory.accessToken(2L, "CARRIER");

    // 미래 시각, deliveryAt > pickupAt (도메인 불변식). 정적 StringBody는 placeholder를 못 채우므로
    // 시뮬레이션 인스턴스 생성 시점에 ISO-8601 문자열로 조립한다.
    String pickupAt   = Instant.now().plus(2, ChronoUnit.HOURS).toString();
    String deliveryAt = Instant.now().plus(24, ChronoUnit.HOURS).toString();
    String createBody = "{ \"shippingAddressId\":1, \"laundromatId\":1, \"laundryItemType\":\"NORMAL\","
        + " \"selectedOptions\":[{\"optionType\":\"WASH\",\"subOptionType\":\"COLD\"}],"
        + " \"desiredPickupAt\":\"" + pickupAt + "\", \"desiredDeliveryAt\":\"" + deliveryAt + "\" }";

    ScenarioBuilder listOrders = scenario("주문 목록 조회")
        .exec(http("GET /orders/my")
            .get("/api/v2/orders/my?size=20")
            .header("Authorization", "Bearer " + customerToken)
            .check(status().is(200)));

    ScenarioBuilder createOrder = scenario("주문 생성")
        .exec(http("POST /orders")
            .post("/api/v2/orders")
            .header("Authorization", "Bearer " + customerToken)
            .body(StringBody(createBody))
            .check(status().is(201)));

    // 배차 수락: available 목록에서 dispatchId를 추출 → claim. PENDING 풀이 유한하므로
    // 사용자 수를 시드 풀 크기 이하로 둔다(Task 8). 풀 고갈 시 409가 나므로 in(200,409) 허용 옵션도 가능.
    ScenarioBuilder claimDispatch = scenario("배차 선점")
        .exec(http("GET /dispatches/available")
            .get("/api/v2/dispatches/available?size=1")
            .header("Authorization", "Bearer " + carrierToken)
            .check(status().is(200))
            .check(jsonPath("$.data[0].id").saveAs("dispatchId")))
        .exec(http("POST /dispatches/{id}/claim")
            .post("/api/v2/dispatches/#{dispatchId}/claim")
            .header("Authorization", "Bearer " + carrierToken)
            .check(status().is(200)));

    {
        setUp(
            listOrders.injectOpen(rampUsers(50).during(30)),
            createOrder.injectOpen(rampUsers(30).during(30)),
            claimDispatch.injectOpen(rampUsers(20).during(30)) // ≤ 시드 PENDING 풀 크기
        ).protocols(httpProtocol)
         .assertions(
            global().responseTime().percentile3().lt(BASELINE_P95_MS), // 최초 측정 후 기준선*1.5로 설정
            global().failedRequests().percent().lt(1.0)
         );
    }

    // 최초 측정 전에는 느슨하게(예: 2000) 두고, 측정 후 기준선 기반으로 교체.
    static final int BASELINE_P95_MS = 2000;
}
```

> 시나리오 ③(배차 선점)은 spec §3.3 item4의 "배차 수락" 경로다. carrier가 단일 토큰(userId=2)을 공유하므로 동시 claim이 같은 행을 노릴 수 있다 — 풀을 사용자 수보다 넉넉히 시드하면 대부분 200. 측정 안정성을 위해 필요시 `status().in(200, 409)`로 완화하고 README에 명시한다.

- [ ] **Step 2: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-loadtest:gatlingClasses`
Expected: 통과.

- [ ] **Step 3: 커밋**

```bash
git add carry-loadtest/src/gatling/java/com/carry/loadtest/CarryLoadSimulation.java
git commit -F- <<'EOF'
test(perf): Gatling 부하 시뮬레이션 3개 시나리오 + assertions (#94)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

### Task 11: 라이브 풀스택 스모크 — 측정·기준선 확정

> **secret/프로파일 일치가 핵심.** bootRun은 `local` 프로파일을 써야 datasource URL·jwt.secret이 바인딩된다(default `application.yml`엔 jwt.secret 없음). `local`의 secret은 `carry-local-dev-secret-key-do-not-use-in-production`(`application-local.yml:25`)이므로, Gatling의 `JWT_SECRET`도 **동일 값**이어야 토큰이 검증된다(불일치 시 전 요청 401 → 실패율 100%).

- [ ] **Step 1: docker 풀스택 기동**

Run: `docker compose up -d` (postgres 5432 + redis + kafka). 기동 확인.

- [ ] **Step 2: 앱 기동 (local 프로파일 → Hibernate가 스키마 생성)**

Run (별도 셸, background):
```
JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" SPRING_PROFILES_ACTIVE=local MANAGEMENT_TRACING_ENABLED=false ./gradlew :carry-app:bootRun
```
(`SPRING_PROFILES_ACTIVE=local` 필수 — datasource/secret 바인딩 + `ddl-auto: update`로 스키마 생성. `MANAGEMENT_TRACING_ENABLED=false`로 otel exporter 블록 회피, 필요시 `-Dotel.sdk.disabled=true`.) `GET /actuator/health` 200 확인.

- [ ] **Step 3: 시드 적용 (스키마 생성 이후)**

Run: Task 8 Step 2의 psql 시드 명령. (Step 2에서 테이블이 생성된 뒤라야 성공.)

- [ ] **Step 4: Gatling 실행 (local secret 주입, BASELINE 느슨한 상태)**

Run:
```
JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" JWT_SECRET=carry-local-dev-secret-key-do-not-use-in-production ./gradlew :carry-loadtest:gatlingRun
```
Expected: 3개 시나리오 실행, HTML 리포트 생성(`carry-loadtest/build/reports/gatling/`). 실패율 1% 미만(401이 대량이면 secret 불일치 → Step 2/4 secret 재확인).

- [ ] **Step 5: p95 측정값 기록 + assertion 임계값 확정**

리포트에서 각 시나리오 p50/p95/p99를 읽어 README에 기준선으로 기록(머신 사양 명시). `BASELINE_P95_MS`를 측정 p95 × 1.5로 교체하고 재실행해 GREEN 확인.

- [ ] **Step 6: 앱·docker 정리**

bootRun 종료, `docker compose down`.

### Task 12: README + 기준선 문서 + 마무리

**Files:**
- Create: `carry-loadtest/README.md`

- [ ] **Step 1: README 작성**

실행 순서(docker compose up → bootRun(`SPRING_PROFILES_ACTIVE=local MANAGEMENT_TRACING_ENABLED=false`) → seed.sql → gatlingRun), 환경변수(`JWT_SECRET=carry-local-dev-secret-key-do-not-use-in-production`로 local secret 일치), 측정된 p50/p95/p99 기준선 표(측정 머신 사양 포함), assertion 정책(기준선×1.5, 실패율<1%), CI 게이트가 아님을 명시.

- [ ] **Step 2: 커밋 (README + Task 11 Step 5에서 확정한 BASELINE_P95_MS)**

`CarryLoadSimulation.java`를 함께 add하는 이유는 Task 11 Step 5에서 `BASELINE_P95_MS`를 실측 기준선으로 교체했기 때문(README의 기준선 표와 동일 커밋으로 묶는다).

```bash
git add carry-loadtest/README.md carry-loadtest/src/gatling/java/com/carry/loadtest/CarryLoadSimulation.java
git commit -F- <<'EOF'
docs(perf): Gatling 부하 기준선 문서 + assertion 임계값 확정 (#94)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

- [ ] **Step 3: 일반 빌드에 Tier2가 영향 없음을 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test`
Expected: GREEN. carry-loadtest는 `gatlingRun`으로만 실행되며 `:carry-app:test`/`check`에 끌려오지 않음을 확인.

---

## 완료 기준 (spec §4 대응)

- [ ] Tier1 쿼리 가드 IT가 N+1 회귀를 RED로 검출(Task 5 teeth 확인 후 원복)
- [ ] Tier1이 `:carry-app:test`에 포함되어 GREEN (매 PR CI 자동)
- [ ] Tier2 Gatling이 3개 시나리오 p95 측정 + 기준선 README 문서화
- [ ] Tier2가 격리 모듈이라 일반 빌드·테스트에 영향 0 (Task 12 Step 3)
- [ ] 프로덕션 비즈니스 로직 무변경 (측정·부하 코드는 test/gatling 소스셋에 격리). 단 가드가 발견한 목록 N+1은 `default_batch_fetch_size` 설정으로 수정 — 성능 회귀 테스트의 본래 목적
- [ ] 라이브 풀스택 스모크(Gatling 실행) 통과

## PR

- base develop, 이슈 #94 연결. 한글 본문.
- dev 머지 = 사용자 게이트(자율 머지 대상이나 부하 측정 결과를 PR 본문에 첨부).

## 리스크 메모

- **목록 N+1이 이미 프로덕션에 존재**: `OrderJpaEntity.selectedOptions` EAGER + fetch-join/batch 부재 + `open-in-view: false`. Task 2 가드는 RED로 시작하며 Task 2 Step 3의 `default_batch_fetch_size`로 해결. 이것이 본 작업이 발견·수정하는 실제 결함.
- **secret/프로파일 일치(Tier2)**: bootRun=`local` 프로파일(secret=`carry-local-dev-secret-key-do-not-use-in-production`). Gatling `JWT_SECRET`을 동일 값으로. 불일치 시 전 요청 401. default 프로파일엔 jwt.secret 없음 → `SPRING_PROFILES_ACTIVE=local` 필수.
- **스키마 생성 순서(Tier2)**: `local`은 flyway off + `ddl-auto: update` → bootRun이 스키마 생성. seed.sql은 **bootRun 이후** 적용(Task 11 Step 2→3 순서).
- **gatling-gradle-plugin × Gradle 8.12.1/JDK21**: Task 7 Step 3에서 호환 확정. 비호환 시 호환 버전으로 핀하고 README에 기록.
- **QueryCountHolder는 ThreadLocal**: Tier1은 단일 스레드 동기 호출만 측정(서비스 직접 호출). 비동기/병렬 경로는 측정 대상 아님.
- **배차 수락 선행 상태**: `claimDispatch`(공개 선점)로 측정 — `acceptAssignment`(ASSIGNED→ACCEPTED)는 coordinator 배정 선행이 필요해 셋업이 복잡. 선점이 carrier의 주 수락 경로. claim은 PENDING 풀을 소비하므로 풀 크기 ≥ claim 사용자 수.
- **Tier2 시드 컬럼 권위**: seed.sql은 `TestFixtures.kt` + **각 모듈** 마이그레이션(`carry-dispatch`/`carry-order`/`carry-user`의 `db/migration`)으로 검증. `carry-app/.../db/migration`엔 outbox 테이블만 있음.
