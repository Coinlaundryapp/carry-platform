# 크로스모듈 Consumer-Driven Contract 테스트 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 4개 크로스모듈 쿼리 포트(User·Payment·Laundromat·ServiceAvailability)에 Consumer-Driven Contract 테스트를 도입해, 소비자 단위테스트가 쓰는 Fake와 carry-app의 real 어댑터가 같은 계약을 통과하도록 강제(드리프트 구조적 차단 + provider 변경 조기 검출).

**Architecture:** 각 포트마다 소비자 모듈 `src/testFixtures`에 **추상 계약 클래스**(`<Port>Contract`)와 **공유 Fake**(`Fake<Port>`)를 둔다. 두 서브클래스가 같은 계약 절을 통과한다 — ①소비자측(`Fake<Port>ContractTest`, 소비자 `src/test`)은 Fake가 계약에 충실함을, ②provider측(`<Port>AdapterContractTest`, `carry-app/src/test`)은 real 어댑터 + real provider 서비스 + in-memory fake persistence가 계약을 준수함을 증명. UserQueryPort만 추가로 Testcontainers 스모크 IT 1개. 마지막에 소비자 단위테스트의 크로스모듈 mock을 검증된 Fake로 교체(옵션 B).

**Tech Stack:** Kotlin 2.1, JUnit5, AssertJ, Gradle `java-test-fixtures`, Spring Boot 3.4.1, Testcontainers(postgis). **계약·Fake(testFixtures)는 mockk 금지**(`testFixturesImplementation`이 `testImplementation`을 상속하지 않음 → junit5+assertj만).

**Spec:** `docs/superpowers/specs/2026-06-08-cross-module-contract-tests-design.md`

**전제:** 브랜치 `feature/cross-module-contract-tests`(origin/develop 분기, 설계 커밋 `d2f7d76` 위). JDK21 prefix: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7"`. 모든 gradle 명령은 이 prefix 사용.

---

## File Structure

**carry-order (소비자: User·Laundromat·ServiceAvailability):**
- Modify: `carry-order/build.gradle.kts` — `java-test-fixtures` 플러그인 + testFixtures deps
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/LaundromatQueryPortContract.kt`
- Create: `.../contract/FakeLaundromatQueryPort.kt`
- Create: `.../contract/ServiceAvailabilityQueryPortContract.kt`
- Create: `.../contract/FakeServiceAvailabilityQueryPort.kt`
- Create: `.../contract/UserQueryPortContract.kt`
- Create: `.../contract/FakeUserQueryPort.kt`
- Create: `carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/Fake{Laundromat,ServiceAvailability,User}QueryPortContractTest.kt` (3)

**carry-delivery (소비자: Payment):**
- Modify: `carry-delivery/build.gradle.kts`
- Create: `carry-delivery/src/testFixtures/kotlin/com/carry/delivery/application/port/outbound/contract/PaymentQueryPortContract.kt`
- Create: `.../contract/FakePaymentQueryPort.kt`
- Create: `carry-delivery/src/test/kotlin/com/carry/delivery/application/port/outbound/contract/FakePaymentQueryPortContractTest.kt`

**carry-app (provider측 검증 + 스모크 IT):**
- Modify: `carry-app/build.gradle.kts` — `testImplementation(testFixtures(project(...)))` 2개
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/fake/Fake{Laundromat,ServiceArea,Payment,ShippingAddress}PersistencePort.kt` (4)
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/{Laundromat,ServiceAvailability,User,Payment}QueryPortAdapterContractTest.kt` (4)
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/UserQueryPortAdapterIntegrationTest.kt` (스모크 IT)

**옵션 B 마이그레이션:**
- Modify: `carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt`
- Modify: `carry-delivery/src/test/kotlin/com/carry/delivery/application/service/DeliveryCommandServiceTest.kt`

---

## Chunk 1: carry-order testFixtures 배관 + 스모크 게이트

소스셋 배선을 4개 계약 작성 전에 먼저 실증한다(스펙 §7 선행 게이트).

### Task 1.1: carry-order에 java-test-fixtures 배관

**Files:**
- Modify: `carry-order/build.gradle.kts`

- [ ] **Step 1: 플러그인 + testFixtures 의존 추가**

`plugins { ... }` 블록에 `id("java-test-fixtures")`를 추가하고, `dependencies { ... }` 블록 끝에 testFixtures 의존을 추가한다. 최종 파일:

```kotlin
plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
    id("java-test-fixtures")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    implementation(project(":carry-common"))
    implementation(project(":carry-event"))
    implementation(project(":carry-audit"))
    implementation(project(":carry-infra-persistence"))
    implementation(project(":carry-infra-kafka"))
    implementation(project(":carry-infra-observability"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // 계약/Fake는 mockk 금지 — junit5 + assertj 만
    testFixturesApi("org.junit.jupiter:junit-jupiter:5.11.3")
    testFixturesApi("org.assertj:assertj-core:3.27.0")
}
```

> `testFixturesApi`로 junit/assertj를 노출 → carry-app이 `testFixtures(project(":carry-order"))`를 의존할 때 전이 확보. testFixtures 소스셋은 main 출력(포트 인터페이스·도메인 vo)을 자동 참조.

- [ ] **Step 2: 빌드 배선 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL (빈 소스셋이라도 태스크 존재·성공). `compileTestFixturesKotlin` 태스크가 인식되면 배선 OK.

### Task 1.2: trivial 픽스처로 소스셋 왕복 실증

**Files:**
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/WiringProbe.kt`
- Create: `carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/WiringProbeTest.kt`

- [ ] **Step 1: testFixtures에 trivial 추상 클래스**

`WiringProbe.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class WiringProbe {
    protected abstract fun value(): Int

    @Test
    fun `testFixtures 소스셋이 test에서 보인다`() {
        assertThat(value()).isEqualTo(42)
    }
}
```

- [ ] **Step 2: test에서 상속**

`WiringProbeTest.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

class WiringProbeTest : WiringProbe() {
    override fun value() = 42
}
```

- [ ] **Step 3: 실행 — test가 testFixtures를 보고 통과**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test --tests "*.WiringProbeTest"`
Expected: PASS (1 test). 배선 실증 완료.

- [ ] **Step 4: probe 제거**

probe는 실증용이므로 삭제한다:
```bash
rm carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/WiringProbe.kt
rm carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/WiringProbeTest.kt
```

- [ ] **Step 5: Commit**

```bash
git add carry-order/build.gradle.kts
git commit -m "build(contract): carry-order에 java-test-fixtures 배관 (CDC 계약 소스셋)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 2: LaundromatQueryPort 계약 (예외-as-시그널, 패턴 템플릿)

가장 단순한 포트로 전체 CDC 패턴(계약·Fake·소비자측·provider측)을 end-to-end 확립.

### Task 2.1: 추상 계약 + Fake (testFixtures)

**Files:**
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/LaundromatQueryPortContract.kt`
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/FakeLaundromatQueryPort.kt`

- [ ] **Step 1: 추상 계약 작성**

`LaundromatQueryPortContract.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * LaundromatQueryPort 소비자(carry-order) 기대 계약.
 * - 소비자측: FakeLaundromatQueryPortContractTest (Fake가 계약 충실)
 * - provider측: LaundromatQueryPortAdapterContractTest (real 어댑터+서비스가 계약 준수)
 */
abstract class LaundromatQueryPortContract {

    protected abstract fun subject(): LaundromatQueryPort

    /** laundromatId가 존재하는 상태로 준비 */
    protected abstract fun arrangeExisting(laundromatId: Long)

    /** laundromatId가 존재하지 않는 상태로 준비 */
    protected abstract fun arrangeMissing(laundromatId: Long)

    @Test
    fun `존재하는 세탁소면 existsById는 true`() {
        arrangeExisting(100L)
        assertThat(subject().existsById(100L)).isTrue()
    }

    @Test
    fun `존재하지 않는 세탁소면 existsById는 false (provider의 NotFound 예외를 false로 변환)`() {
        arrangeMissing(999L)
        assertThat(subject().existsById(999L)).isFalse()
    }
}
```

- [ ] **Step 2: 공유 Fake 작성**

`FakeLaundromatQueryPort.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort

class FakeLaundromatQueryPort : LaundromatQueryPort {
    private val existingIds = mutableSetOf<Long>()

    fun add(laundromatId: Long) {
        existingIds += laundromatId
    }

    fun clear() {
        existingIds.clear()
    }

    override fun existsById(laundromatId: Long): Boolean = laundromatId in existingIds
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

### Task 2.2: 소비자측 계약 테스트 (Fake가 계약 충실)

**Files:**
- Create: `carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/FakeLaundromatQueryPortContractTest.kt`

- [ ] **Step 1: 작성**

```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort

class FakeLaundromatQueryPortContractTest : LaundromatQueryPortContract() {

    private val fake = FakeLaundromatQueryPort()

    override fun subject(): LaundromatQueryPort = fake

    override fun arrangeExisting(laundromatId: Long) {
        fake.add(laundromatId)
    }

    override fun arrangeMissing(laundromatId: Long) {
        // 추가하지 않음 = 부재
    }
}
```

- [ ] **Step 2: 실행 — 통과**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test --tests "*.FakeLaundromatQueryPortContractTest"`
Expected: PASS (2 tests)

### Task 2.3: provider측 계약 테스트 (real 어댑터 + fake persistence)

**Files:**
- Create: `carry-app/build.gradle.kts` 수정 (testFixtures 의존)
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/fake/FakeLaundromatPersistencePort.kt`
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/LaundromatQueryPortAdapterContractTest.kt`

- [ ] **Step 1: carry-app이 carry-order testFixtures 의존**

`carry-app/build.gradle.kts`의 test 의존 영역(예: `testImplementation("org.assertj:assertj-core:3.27.0")` 다음 줄)에 추가:
```kotlin
    testImplementation(testFixtures(project(":carry-order")))
```

- [ ] **Step 2: fake LaundromatPersistencePort**

`FakeLaundromatPersistencePort.kt`:
```kotlin
package com.carry.app.contract.fake

import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.Location
import java.time.Instant

class FakeLaundromatPersistencePort : LaundromatPersistencePort {
    private val store = mutableMapOf<Long, Laundromat>()

    fun put(id: Long) {
        store[id] = Laundromat.reconstitute(
            id = id,
            name = "테스트세탁소",
            address = LaundromatAddress("서울시 강남구 테헤란로 1"),
            location = Location(37.5, 127.0),
            options = emptySet(),
            mediaResources = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    override fun save(laundromat: Laundromat): Laundromat = laundromat
    override fun findById(id: Long): Laundromat? = store[id]
    override fun findNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<NearbyLaundromat> = emptyList()
    override fun delete(id: Long) { store.remove(id) }
}
```

- [ ] **Step 3: provider측 계약 테스트**

`LaundromatQueryPortAdapterContractTest.kt`:
```kotlin
package com.carry.app.contract

import com.carry.app.adapter.LaundromatQueryPortAdapter
import com.carry.app.contract.fake.FakeLaundromatPersistencePort
import com.carry.laundromat.application.service.LaundromatQueryService
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.contract.LaundromatQueryPortContract

class LaundromatQueryPortAdapterContractTest : LaundromatQueryPortContract() {

    private val persistence = FakeLaundromatPersistencePort()
    private val adapter = LaundromatQueryPortAdapter(LaundromatQueryService(persistence))

    override fun subject(): LaundromatQueryPort = adapter

    override fun arrangeExisting(laundromatId: Long) {
        persistence.put(laundromatId)
    }

    override fun arrangeMissing(laundromatId: Long) {
        // put 하지 않음 → findById null → 서비스가 LaundromatNotFoundException → 어댑터가 false
    }
}
```

- [ ] **Step 4: 실행 — real 어댑터가 같은 계약 통과**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.LaundromatQueryPortAdapterContractTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add carry-order/src/testFixtures carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract carry-app/build.gradle.kts carry-app/src/test/kotlin/com/carry/app/contract
git commit -m "test(contract): LaundromatQueryPort CDC 계약 (예외-as-시그널)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 3: ServiceAvailabilityQueryPort 계약 (예외-as-시그널, 두 instant)

### Task 3.1: 추상 계약 + Fake (testFixtures)

**Files:**
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/ServiceAvailabilityQueryPortContract.kt`
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/FakeServiceAvailabilityQueryPort.kt`

- [ ] **Step 1: 추상 계약**

> 결정적 픽스처를 쓰기 위해 KST 정오에 해당하는 고정 instant 사용(KST 자정 회피). pickup/delivery 둘 다 검증됨을 계약에 반영.

`ServiceAvailabilityQueryPortContract.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

abstract class ServiceAvailabilityQueryPortContract {

    protected val areaCode = "GANGNAM"
    // 2026-06-08 12:00 KST = 03:00Z (월요일), delivery 익일 정오
    protected val pickupAt: Instant = Instant.parse("2026-06-08T03:00:00Z")
    protected val deliveryAt: Instant = Instant.parse("2026-06-09T03:00:00Z")

    protected abstract fun subject(): ServiceAvailabilityQueryPort

    /** areaCode가 ACTIVE이고 pickup·delivery 두 시각 모두 운영시간 내인 상태 */
    protected abstract fun arrangeAvailable()

    /** areaCode 자체가 존재하지 않는 상태 */
    protected abstract fun arrangeMissingArea()

    /** areaCode는 있으나 delivery 시각이 운영시간 밖인 상태 */
    protected abstract fun arrangeDeliveryOutsideHours()

    @Test
    fun `가용하면 예외 없이 통과`() {
        arrangeAvailable()
        assertThatCode { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `area가 없으면 예외`() {
        arrangeMissingArea()
        assertThatThrownBy { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `delivery 시각이 운영시간 밖이면 예외 (두 instant 모두 검증)`() {
        arrangeDeliveryOutsideHours()
        assertThatThrownBy { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
```

- [ ] **Step 2: 공유 Fake**

> 소비자(carry-order)는 service-availability 도메인/예외에 의존하지 않으므로 Fake는 generic 예외를 던진다(계약은 "throws"만 단언).

`FakeServiceAvailabilityQueryPort.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import java.time.Instant

class FakeServiceAvailabilityQueryPort : ServiceAvailabilityQueryPort {

    /** 가용으로 표시된 (areaCode) 집합 */
    private val available = mutableSetOf<String>()
    /** 이 시각엔 불가로 표시된 (areaCode, instant) */
    private val unavailableInstants = mutableSetOf<Pair<String, Instant>>()

    fun markAvailable(areaCode: String) {
        available += areaCode
    }

    fun markUnavailableAt(areaCode: String, instant: Instant) {
        available += areaCode
        unavailableInstants += areaCode to instant
    }

    override fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant) {
        if (areaCode !in available) {
            throw IllegalStateException("서비스 지역 없음: $areaCode")
        }
        if (areaCode to pickupAt in unavailableInstants || areaCode to deliveryAt in unavailableInstants) {
            throw IllegalStateException("운영시간 밖: $areaCode")
        }
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

### Task 3.2: 소비자측 계약 테스트

**Files:**
- Create: `carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/FakeServiceAvailabilityQueryPortContractTest.kt`

- [ ] **Step 1: 작성**

```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort

class FakeServiceAvailabilityQueryPortContractTest : ServiceAvailabilityQueryPortContract() {

    private val fake = FakeServiceAvailabilityQueryPort()

    override fun subject(): ServiceAvailabilityQueryPort = fake

    override fun arrangeAvailable() {
        fake.markAvailable(areaCode)
    }

    override fun arrangeMissingArea() {
        // markAvailable 하지 않음
    }

    override fun arrangeDeliveryOutsideHours() {
        fake.markUnavailableAt(areaCode, deliveryAt)
    }
}
```

- [ ] **Step 2: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test --tests "*.FakeServiceAvailabilityQueryPortContractTest"`
Expected: PASS (3 tests)

### Task 3.3: provider측 계약 테스트

**Files:**
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/fake/FakeServiceAreaPersistencePort.kt`
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/ServiceAvailabilityQueryPortAdapterContractTest.kt`

- [ ] **Step 1: fake ServiceAreaPersistencePort**

`FakeServiceAreaPersistencePort.kt`:
```kotlin
package com.carry.app.contract.fake

import com.carry.serviceavailability.application.port.outbound.ServiceAreaPersistencePort
import com.carry.serviceavailability.domain.model.ServiceArea

class FakeServiceAreaPersistencePort : ServiceAreaPersistencePort {
    private val byAreaCode = mutableMapOf<String, ServiceArea>()

    fun put(area: ServiceArea) {
        byAreaCode[area.areaCode] = area
    }

    override fun save(serviceArea: ServiceArea): ServiceArea = serviceArea
    override fun findById(id: Long): ServiceArea? = byAreaCode.values.find { it.id == id }
    override fun findByAreaCode(areaCode: String): ServiceArea? = byAreaCode[areaCode]
    override fun findAllActive(): List<ServiceArea> = byAreaCode.values.toList()
    override fun existsByAreaCode(areaCode: String): Boolean = areaCode in byAreaCode
    override fun delete(id: Long) { byAreaCode.values.removeIf { it.id == id } }
}
```

- [ ] **Step 2: provider측 계약 테스트**

> real `ServiceArea`를 공개 API로 구성: `create` → `activate` → 7일 전일 스케줄. "운영시간 밖"은 월요일(pickup·delivery 둘 다 월요일이지만 deliveryAt만 슬롯 밖이 되도록) 스케줄을 좁혀 구성. 단순화를 위해 delivery 시각(정오)을 포함하지 않는 좁은 슬롯(00:00~00:01)만 두면 pickup·delivery 둘 다 밖이 되어 "throws"는 성립하나 "delivery 검증" 의도가 약하므로, pickup은 포함하고 delivery는 제외하는 슬롯을 만든다. pickupAt=03:00Z=12:00KST, deliveryAt=익일 12:00KST(동일 월요일 아님 → 화요일). 따라서 **월요일은 전일 운영(pickup 통과), 화요일만 좁은 슬롯(11:00~11:30, delivery 정오 제외)**으로 두면 delivery만 밖.

`ServiceAvailabilityQueryPortAdapterContractTest.kt`:
```kotlin
package com.carry.app.contract

import com.carry.app.adapter.ServiceAvailabilityQueryPortAdapter
import com.carry.app.contract.fake.FakeServiceAreaPersistencePort
import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.contract.ServiceAvailabilityQueryPortContract
import com.carry.serviceavailability.application.service.ServiceAvailabilityQueryService
import com.carry.serviceavailability.domain.model.ServiceArea
import java.time.DayOfWeek
import java.time.LocalTime

class ServiceAvailabilityQueryPortAdapterContractTest : ServiceAvailabilityQueryPortContract() {

    private val persistence = FakeServiceAreaPersistencePort()
    private val adapter = ServiceAvailabilityQueryPortAdapter(ServiceAvailabilityQueryService(persistence))

    override fun subject(): ServiceAvailabilityQueryPort = adapter

    override fun arrangeAvailable() {
        persistence.put(activeAllWeekArea())
    }

    override fun arrangeMissingArea() {
        // put 하지 않음
    }

    override fun arrangeDeliveryOutsideHours() {
        // pickupAt(월 12:00 KST)은 전일 운영으로 통과, deliveryAt(화 12:00 KST)는 좁은 슬롯 밖
        val area = ServiceArea.create(areaCode, "강남구").apply {
            activate()
            // pickup 요일(월) 전일 운영
            setSchedule(pickupAt.atZone(KST).dayOfWeek, LocalTime.MIN, LocalTime.of(23, 59))
            // delivery 요일(화) 좁은 슬롯 — 정오 미포함
            setSchedule(deliveryAt.atZone(KST).dayOfWeek, LocalTime.of(11, 0), LocalTime.of(11, 30))
        }
        persistence.put(area)
    }

    private fun activeAllWeekArea(): ServiceArea =
        ServiceArea.create(areaCode, "강남구").apply {
            activate()
            DayOfWeek.entries.forEach { setSchedule(it, LocalTime.MIN, LocalTime.of(23, 59)) }
        }

    companion object {
        private val KST = java.time.ZoneId.of("Asia/Seoul")
    }
}
```

> ⚠️ `deliveryAt = 2026-06-09T03:00:00Z` = 2026-06-09 12:00 KST. 2026-06-08은 월요일이므로 06-09는 화요일. `pickupAt`은 월요일. 두 요일이 달라 위 스케줄 분리가 성립한다. (계약의 고정 instant가 바뀌면 이 요일 가정도 재확인.)

- [ ] **Step 3: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.ServiceAvailabilityQueryPortAdapterContractTest"`
Expected: PASS (3 tests)

- [ ] **Step 4: Commit**

```bash
git add carry-order/src/testFixtures carry-order/src/test carry-app/src/test/kotlin/com/carry/app/contract
git commit -m "test(contract): ServiceAvailabilityQueryPort CDC 계약 (두 instant 예외-as-시그널)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 4: UserQueryPort 계약 (9필드 매핑) + 스모크 IT

### Task 4.1: 추상 계약 + Fake (testFixtures)

**Files:**
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/UserQueryPortContract.kt`
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/FakeUserQueryPort.kt`

- [ ] **Step 1: 추상 계약**

> §3 타입 비대칭: `expected.zipCode`는 non-null·non-blank만(real측 arrange 가능 영역). zipCode=null 절은 제외.

`UserQueryPortContract.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

abstract class UserQueryPortContract {

    protected abstract fun subject(): UserQueryPort

    /** (userId, addressId)에 expected와 동치인 주소를 준비. expected.zipCode는 non-null·non-blank. */
    protected abstract fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress)

    /** (userId, addressId)에 주소가 없는 상태 */
    protected abstract fun arrangeMissing(userId: Long, addressId: Long)

    @Test
    fun `9필드가 정확히 매핑된다`() {
        val expected = OrderShippingAddress(
            roadAddress = "서울시 강남구 테헤란로 123",
            detailAddress = "4층 401호",
            zipCode = "06234",
            latitude = 37.5065,
            longitude = 127.0536,
            recipientName = "홍길동",
            recipientPhone = "01012345678",
            entranceInfo = "현관 비밀번호 1234",
            areaCode = "GANGNAM",
        )
        arrangeAddress(1L, 10L, expected)
        assertThat(subject().getShippingAddress(1L, 10L)).isEqualTo(expected)
    }

    @Test
    fun `nullable entranceInfo가 null로 보존된다`() {
        val expected = OrderShippingAddress(
            roadAddress = "서울시 강남구 테헤란로 123",
            detailAddress = "4층",
            zipCode = "06234",
            latitude = 37.5,
            longitude = 127.0,
            recipientName = "김철수",
            recipientPhone = "01099998888",
            entranceInfo = null,
            areaCode = "GANGNAM",
        )
        arrangeAddress(2L, 20L, expected)
        assertThat(subject().getShippingAddress(2L, 20L).entranceInfo).isNull()
    }

    @Test
    fun `주소가 없으면 예외가 전파된다`() {
        arrangeMissing(3L, 30L)
        assertThatThrownBy { subject().getShippingAddress(3L, 30L) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
```

- [ ] **Step 2: 공유 Fake**

`FakeUserQueryPort.kt`:
```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress

class FakeUserQueryPort : UserQueryPort {
    private val store = mutableMapOf<Pair<Long, Long>, OrderShippingAddress>()

    fun put(userId: Long, addressId: Long, address: OrderShippingAddress) {
        store[userId to addressId] = address
    }

    override fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress =
        store[userId to addressId]
            ?: throw NoSuchElementException("주소 없음: user=$userId, address=$addressId")
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

### Task 4.2: 소비자측 계약 테스트

**Files:**
- Create: `carry-order/src/test/kotlin/com/carry/order/application/port/outbound/contract/FakeUserQueryPortContractTest.kt`

- [ ] **Step 1: 작성**

```kotlin
package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress

class FakeUserQueryPortContractTest : UserQueryPortContract() {

    private val fake = FakeUserQueryPort()

    override fun subject(): UserQueryPort = fake

    override fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress) {
        fake.put(userId, addressId, expected)
    }

    override fun arrangeMissing(userId: Long, addressId: Long) {
        // put 하지 않음
    }
}
```

- [ ] **Step 2: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test --tests "*.FakeUserQueryPortContractTest"`
Expected: PASS (3 tests)

### Task 4.3: provider측 계약 테스트 (9필드 매핑 검증)

**Files:**
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/fake/FakeShippingAddressPersistencePort.kt`
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/UserQueryPortAdapterContractTest.kt`

- [ ] **Step 1: fake ShippingAddressPersistencePort**

`FakeShippingAddressPersistencePort.kt`:
```kotlin
package com.carry.app.contract.fake

import com.carry.user.application.port.outbound.ShippingAddressPersistencePort
import com.carry.user.domain.model.ShippingAddress

class FakeShippingAddressPersistencePort : ShippingAddressPersistencePort {
    private val byId = mutableMapOf<Long, ShippingAddress>()

    fun put(id: Long, address: ShippingAddress) {
        byId[id] = address
    }

    override fun save(address: ShippingAddress): ShippingAddress = address
    override fun findById(id: Long): ShippingAddress? = byId[id]
    override fun findByUserId(userId: Long): List<ShippingAddress> = byId.values.filter { it.userId == userId }
    override fun findDefaultByUserId(userId: Long): ShippingAddress? = byId.values.find { it.userId == userId && it.isDefault }
    override fun countByUserId(userId: Long): Long = byId.values.count { it.userId == userId }.toLong()
    override fun delete(address: ShippingAddress) { byId.values.removeIf { it.id == address.id } }
}
```

- [ ] **Step 2: provider측 계약 테스트**

> real측 arrange: expected와 동치인 provider-domain `ShippingAddress`를 `reconstitute`로 구성(9 매핑 필드 + 비매핑 filler `alias/isDefault/createdAt/updatedAt`). `userId`는 arrange 키. `zipCode`는 non-null(`expected.zipCode!!`).

`UserQueryPortAdapterContractTest.kt`:
```kotlin
package com.carry.app.contract

import com.carry.app.adapter.UserQueryPortAdapter
import com.carry.app.contract.fake.FakeShippingAddressPersistencePort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.application.port.outbound.contract.UserQueryPortContract
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.user.application.service.ShippingAddressService
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import java.time.Instant

class UserQueryPortAdapterContractTest : UserQueryPortContract() {

    private val persistence = FakeShippingAddressPersistencePort()
    private val adapter = UserQueryPortAdapter(ShippingAddressService(persistence))

    override fun subject(): UserQueryPort = adapter

    override fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress) {
        persistence.put(
            addressId,
            ShippingAddress.reconstitute(
                id = addressId,
                userId = userId,
                alias = "집",                          // 비매핑 filler
                address = Address(
                    roadAddress = expected.roadAddress,
                    detailAddress = expected.detailAddress,
                    zipCode = requireNotNull(expected.zipCode) { "계약상 zipCode는 non-null" },
                ),
                coordinates = Coordinates(expected.latitude, expected.longitude),
                recipientName = expected.recipientName,
                recipientPhone = expected.recipientPhone,
                entranceInfo = expected.entranceInfo,
                areaCode = expected.areaCode,
                isDefault = false,                      // 비매핑 filler
                createdAt = Instant.EPOCH,              // 비매핑 filler
                updatedAt = Instant.EPOCH,              // 비매핑 filler
            ),
        )
    }

    override fun arrangeMissing(userId: Long, addressId: Long) {
        // put 하지 않음 → findById null → ShippingAddressNotFoundException 전파
    }
}
```

- [ ] **Step 3: 실행 — real 어댑터의 9필드 매핑 검증**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.UserQueryPortAdapterContractTest"`
Expected: PASS (3 tests)

### Task 4.4: UserQueryPort 스모크 IT (real JPA/Postgres)

**Files:**
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/UserQueryPortAdapterIntegrationTest.kt`

- [ ] **Step 1: 스모크 IT 작성**

> `IntegrationTestBase`(공유 postgis) 상속. `TestFixtures.insertCustomer` + `insertShippingAddress`로 real JPA INSERT 후, 주입받은 real `UserQueryPortAdapter`로 9필드 매핑을 검증(JPA 엔티티/컬럼 드리프트 커버). **고유 userId/addressId**로 saga IT와 시드 충돌 회피. `insertShippingAddress`가 넣는 값과 단언을 일치시킨다.

`UserQueryPortAdapterIntegrationTest.kt`:
```kotlin
package com.carry.app.contract

import com.carry.app.adapter.UserQueryPortAdapter
import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class UserQueryPortAdapterIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var userQueryPortAdapter: UserQueryPortAdapter

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() {
        // FK: user_shipping_addresses.user_id → user_users(id) → 자식 먼저 삭제
        jdbcTemplate.update("DELETE FROM user_shipping_addresses WHERE id = ?", SMOKE_ADDRESS_ID)
        jdbcTemplate.update("DELETE FROM user_users WHERE id = ?", SMOKE_USER_ID)
    }

    @Test
    fun `real JPA로 저장한 주소가 어댑터를 통해 9필드 매핑된다`() {
        TestFixtures.insertCustomer(jdbcTemplate, id = SMOKE_USER_ID)
        TestFixtures.insertShippingAddress(jdbcTemplate, userId = SMOKE_USER_ID, id = SMOKE_ADDRESS_ID)

        val result = userQueryPortAdapter.getShippingAddress(SMOKE_USER_ID, SMOKE_ADDRESS_ID)

        // insertShippingAddress가 넣는 값과 일치
        assertThat(result.roadAddress).isEqualTo("서울시 강남구 테헤란로 123")
        assertThat(result.detailAddress).isEqualTo("4층")
        assertThat(result.zipCode).isEqualTo("06234")
        assertThat(result.latitude).isEqualTo(37.5065)
        assertThat(result.longitude).isEqualTo(127.0536)
        assertThat(result.recipientName).isEqualTo("테스트고객")
        assertThat(result.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(result.areaCode).isEqualTo(TestFixtures.AREA_CODE)
        // insertShippingAddress는 entrance_info를 넣지 않음 → NULL 매핑(JPA nullable 컬럼 커버)
        assertThat(result.entranceInfo).isNull()
    }

    companion object {
        private const val SMOKE_USER_ID = 90001L
        private const val SMOKE_ADDRESS_ID = 90002L
    }
}
```

> ℹ️ 검증 완료(리뷰): 고객 테이블은 `user_users`(위 tearDown에 반영), `TestFixtures.AREA_CODE`는 `public const val "GANGNAM"`(carry-app 테스트 접근 가능), 단언값은 `insertShippingAddress` 삽입값과 일치.

- [ ] **Step 2: 실행 (Testcontainers — 느림)**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.UserQueryPortAdapterIntegrationTest"`
Expected: PASS (1 test). Docker 필요.

- [ ] **Step 3: Commit**

```bash
git add carry-order/src/testFixtures carry-order/src/test carry-app/src/test/kotlin/com/carry/app/contract
git commit -m "test(contract): UserQueryPort CDC 계약(9필드 매핑) + Testcontainers 스모크 IT

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 5: carry-delivery testFixtures 배관 + PaymentQueryPort 계약

### Task 5.1: carry-delivery에 java-test-fixtures 배관

**Files:**
- Modify: `carry-delivery/build.gradle.kts`

- [ ] **Step 1: 플러그인 + testFixtures 의존 추가**

`plugins`에 `id("java-test-fixtures")` 추가, `dependencies` 끝에:
```kotlin
    testFixturesApi("org.junit.jupiter:junit-jupiter:5.11.3")
    testFixturesApi("org.assertj:assertj-core:3.27.0")
```

- [ ] **Step 2: 배선 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-delivery:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

### Task 5.2: 추상 계약 + Fake (testFixtures)

**Files:**
- Create: `carry-delivery/src/testFixtures/kotlin/com/carry/delivery/application/port/outbound/contract/PaymentQueryPortContract.kt`
- Create: `carry-delivery/src/testFixtures/kotlin/com/carry/delivery/application/port/outbound/contract/FakePaymentQueryPort.kt`

- [ ] **Step 1: 추상 계약**

`PaymentQueryPortContract.kt`:
```kotlin
package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class PaymentQueryPortContract {

    protected abstract fun subject(): PaymentQueryPort

    /** 주문이 결제완료(COMPLETED) 상태 */
    protected abstract fun arrangePaid(orderId: Long)
    /** 주문 결제가 존재하나 미완료(예: PENDING) */
    protected abstract fun arrangePendingPayment(orderId: Long)
    /** 주문에 결제 자체가 없음 */
    protected abstract fun arrangeNoPayment(orderId: Long)

    @Test
    fun `결제완료면 isOrderPaid는 true`() {
        arrangePaid(100L)
        assertThat(subject().isOrderPaid(100L)).isTrue()
    }

    @Test
    fun `결제가 미완료면 false`() {
        arrangePendingPayment(200L)
        assertThat(subject().isOrderPaid(200L)).isFalse()
    }

    @Test
    fun `결제가 없으면 false`() {
        arrangeNoPayment(300L)
        assertThat(subject().isOrderPaid(300L)).isFalse()
    }
}
```

- [ ] **Step 2: 공유 Fake**

`FakePaymentQueryPort.kt`:
```kotlin
package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort

class FakePaymentQueryPort : PaymentQueryPort {
    private val paidOrders = mutableSetOf<Long>()

    fun markPaid(orderId: Long) {
        paidOrders += orderId
    }

    override fun isOrderPaid(orderId: Long): Boolean = orderId in paidOrders
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-delivery:compileTestFixturesKotlin`
Expected: BUILD SUCCESSFUL

### Task 5.3: 소비자측 계약 테스트

**Files:**
- Create: `carry-delivery/src/test/kotlin/com/carry/delivery/application/port/outbound/contract/FakePaymentQueryPortContractTest.kt`

- [ ] **Step 1: 작성**

```kotlin
package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort

class FakePaymentQueryPortContractTest : PaymentQueryPortContract() {

    private val fake = FakePaymentQueryPort()

    override fun subject(): PaymentQueryPort = fake

    override fun arrangePaid(orderId: Long) {
        fake.markPaid(orderId)
    }

    override fun arrangePendingPayment(orderId: Long) {
        // markPaid 하지 않음 (Fake는 paid 여부만 추적; 미완료도 not-paid)
    }

    override fun arrangeNoPayment(orderId: Long) {
        // markPaid 하지 않음
    }
}
```

- [ ] **Step 2: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-delivery:test --tests "*.FakePaymentQueryPortContractTest"`
Expected: PASS (3 tests)

### Task 5.4: provider측 계약 테스트

**Files:**
- Modify: `carry-app/build.gradle.kts` — testFixtures(carry-delivery) 의존 추가
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/fake/FakePaymentPersistencePort.kt`
- Create: `carry-app/src/test/kotlin/com/carry/app/contract/PaymentQueryPortAdapterContractTest.kt`

- [ ] **Step 1: carry-app이 carry-delivery testFixtures 의존**

`carry-app/build.gradle.kts`에 추가:
```kotlin
    testImplementation(testFixtures(project(":carry-delivery")))
```

- [ ] **Step 2: fake PaymentPersistencePort**

> Payment를 `reconstitute`로 구성. PENDING/COMPLETED 상태를 직접 지정.

`FakePaymentPersistencePort.kt`:
```kotlin
package com.carry.app.contract.fake

import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import java.time.Instant

class FakePaymentPersistencePort : PaymentPersistencePort {
    private val byOrderId = mutableMapOf<Long, Payment>()

    fun put(orderId: Long, status: PaymentStatus) {
        byOrderId[orderId] = Payment.reconstitute(
            id = orderId,                 // 임의 유효값
            invoiceId = orderId,
            orderId = orderId,
            customerId = 1L,
            status = status,
            pgProvider = PgProvider.TOSS_PAYMENTS,
            pgTransactionId = null,
            amount = 10_000L,
            paidAt = if (status == PaymentStatus.COMPLETED) Instant.EPOCH else null,
            failReason = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    override fun save(payment: Payment): Payment = payment
    override fun findById(id: Long): Payment? = byOrderId.values.find { it.id == id }
    override fun findByOrderId(orderId: Long): Payment? = byOrderId[orderId]
}
```

- [ ] **Step 3: provider측 계약 테스트**

`PaymentQueryPortAdapterContractTest.kt`:
```kotlin
package com.carry.app.contract

import com.carry.app.adapter.PaymentQueryPortAdapter
import com.carry.app.contract.fake.FakePaymentPersistencePort
import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.delivery.application.port.outbound.contract.PaymentQueryPortContract
import com.carry.payment.application.service.PaymentQueryService
import com.carry.payment.domain.vo.PaymentStatus

class PaymentQueryPortAdapterContractTest : PaymentQueryPortContract() {

    private val persistence = FakePaymentPersistencePort()
    private val adapter = PaymentQueryPortAdapter(PaymentQueryService(persistence))

    override fun subject(): PaymentQueryPort = adapter

    override fun arrangePaid(orderId: Long) {
        persistence.put(orderId, PaymentStatus.COMPLETED)
    }

    override fun arrangePendingPayment(orderId: Long) {
        persistence.put(orderId, PaymentStatus.PENDING)
    }

    override fun arrangeNoPayment(orderId: Long) {
        // put 하지 않음 → findByOrderId null → isOrderPaid false
    }
}
```

- [ ] **Step 4: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.PaymentQueryPortAdapterContractTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add carry-delivery/build.gradle.kts carry-delivery/src/testFixtures carry-delivery/src/test carry-app/build.gradle.kts carry-app/src/test/kotlin/com/carry/app/contract
git commit -m "test(contract): PaymentQueryPort CDC 계약 + carry-delivery testFixtures 배관

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 6: 옵션 B 마이그레이션 + teeth 검증 + 최종 게이트

### Task 6.1: OrderCommandServiceTest의 크로스모듈 mock → 검증된 Fake

**Files:**
- Modify: `carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt`

- [ ] **Step 1: 현재 mock 셋업·스텁 파악**

Read: `carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt`
대상: `userQueryPort`/`laundromatQueryPort`/`serviceAvailabilityQueryPort`의 `mockk<...>()` 선언과 각 `every { ... }` 스텁(부재 케이스 포함). **같은 모듈 포트(`orderPersistencePort`)·`eventPublisher`·`metrics`·`auditPort`는 그대로 mockk 유지.**

- [ ] **Step 2: 3개 크로스모듈 포트를 Fake로 교체**

import에 추가:
```kotlin
import com.carry.order.application.port.outbound.contract.FakeUserQueryPort
import com.carry.order.application.port.outbound.contract.FakeLaundromatQueryPort
import com.carry.order.application.port.outbound.contract.FakeServiceAvailabilityQueryPort
```

선언 교체:
```kotlin
// 변경 전
private val userQueryPort = mockk<UserQueryPort>()
private val laundromatQueryPort = mockk<LaundromatQueryPort>()
private val serviceAvailabilityQueryPort = mockk<ServiceAvailabilityQueryPort>(relaxed = true)
// 변경 후
private val userQueryPort = FakeUserQueryPort()
private val laundromatQueryPort = FakeLaundromatQueryPort()
private val serviceAvailabilityQueryPort = FakeServiceAvailabilityQueryPort()
```

각 테스트의 `every { userQueryPort.getShippingAddress(...) } returns address` →
arrange로 교체(예: `userQueryPort.put(userId, addressId, address)`); `every { laundromatQueryPort.existsById(...) } returns true` → `laundromatQueryPort.add(laundromatId)`; serviceAvailability 가용 케이스 → `serviceAvailabilityQueryPort.markAvailable(areaCode)`. 부재/불가/예외를 기대하던 케이스는 해당 arrange를 생략하거나 `markUnavailableAt(...)` 사용. `verify { ... }`로 포트 호출을 검증하던 단언은 Fake로는 불가하므로, 그 의도가 살아있다면 결과·상태 단언으로 치환(불필요한 상호작용 검증이면 삭제).

> ⚠️ Fake는 `relaxed mockk`와 달리 미설정 호출에 기본값을 주지 않는다. 누락된 arrange는 테스트가 드러낸 실제 의존이므로 명시적으로 채운다. areaCode/addressId 값은 각 테스트의 `aCommand()`·`address` 픽스처와 일치시킬 것.
>
> 케이스별 arrange 가이드(리뷰 확인):
> - **주문생성 happy-path**: `userQueryPort.put(...)` + `laundromatQueryPort.add(laundromatId)` + `serviceAvailabilityQueryPort.markAvailable("GANGNAM")`(= `address.areaCode`).
> - **"존재하지 않는 세탁소" 테스트**: `getShippingAddress`가 `existsById`보다 먼저 호출되므로 `userQueryPort.put(...)`는 유지하되, `laundromatQueryPort.add(...)`·`serviceAvailabilityQueryPort.markAvailable(...)`는 생략(laundromat 부재→false 경로).
> - **취소(CancelOrder) 테스트**: 크로스모듈 포트 미사용 → arrange 불필요.
> - 모든 `verify {}`는 `eventPublisher`/`metrics`/`auditPort`(mockk 유지)에만 있으므로 그대로 둔다.

- [ ] **Step 3: 실행 — 전체 통과**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test --tests "*.OrderCommandServiceTest"`
Expected: PASS (기존 테스트 수 유지). 실패 시 누락 arrange 보완.

- [ ] **Step 4: Commit**

```bash
git add carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt
git commit -m "test(order): 크로스모듈 포트 mock을 검증된 Fake로 교체 (CDC keystone)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 6.2: DeliveryCommandServiceTest의 PaymentQueryPort mock → Fake

**Files:**
- Modify: `carry-delivery/src/test/kotlin/com/carry/delivery/application/service/DeliveryCommandServiceTest.kt`

- [ ] **Step 1: 교체**

`mockk<PaymentQueryPort>()` → `FakePaymentQueryPort()`. `every { paymentQueryPort.isOrderPaid(id) } returns true` → `paymentQueryPort.markPaid(id)`. false 기대 케이스는 markPaid 생략. import 추가:
```kotlin
import com.carry.delivery.application.port.outbound.contract.FakePaymentQueryPort
```

- [ ] **Step 2: 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-delivery:test --tests "*.DeliveryCommandServiceTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add carry-delivery/src/test/kotlin/com/carry/delivery/application/service/DeliveryCommandServiceTest.kt
git commit -m "test(delivery): PaymentQueryPort mock을 검증된 Fake로 교체 (CDC keystone)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 6.3: teeth 검증 (뮤테이션 4건 — 계약이 무는지)

> 각 뮤테이션은 **임시**다. 적용→해당 provider측 계약 RED 확인→**즉시 원복**. 커밋하지 않는다.

- [ ] **Step 1: User 매핑 swap**

`carry-app/.../adapter/UserQueryPortAdapter.kt`에서 `recipientName`↔`recipientPhone`을 맞바꾼다.
Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:test --tests "*.UserQueryPortAdapterContractTest"`
Expected: **FAIL** (9필드 매핑 절). 확인 후 원복.

- [ ] **Step 2: Laundromat catch 제거**

`LaundromatQueryPortAdapter.kt`의 `catch (_: LaundromatNotFoundException) { false }`를 제거(예외 전파되게).
Run: `... --tests "*.LaundromatQueryPortAdapterContractTest"`
Expected: **FAIL** (부재→false 절). 원복.

- [ ] **Step 3: Payment 조건 완화**

`PaymentQueryService.isOrderPaid`의 `payment.status == PaymentStatus.COMPLETED`를 `payment != null`로 약화.
Run: `... --tests "*.PaymentQueryPortAdapterContractTest"`
Expected: **FAIL** (미완료→false 절). 원복.

- [ ] **Step 4: ServiceAvailability 두번째 호출 제거**

`ServiceAvailabilityQueryService.checkAvailability`에서 `area.checkAvailability(deliveryAt)` 줄을 제거.
Run: `... --tests "*.ServiceAvailabilityQueryPortAdapterContractTest"`
Expected: **FAIL** (delivery 시각 밖 절). 원복.

- [ ] **Step 5: 원복 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" git status --porcelain`
Expected: 출력 없음(뮤테이션 전부 원복, working tree clean).

### Task 6.4: 최종 전체 게이트

- [ ] **Step 1: 영향 모듈 + 조립 모듈 전체 테스트**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-order:test :carry-delivery:test :carry-app:test`
Expected: BUILD SUCCESSFUL (전체 GREEN, 신규 계약 테스트 + 기존 회귀 포함)

- [ ] **Step 2: 푸시 + PR**

> 이슈 먼저 등록(한글) 후 PR(base develop). 본 작업은 테스트 전용·프로덕션 코드 변경 0(teeth는 원복)이므로 라이브 풀스택 스모크는 생략, CI green이 게이트.

```bash
git push -u origin feature/cross-module-contract-tests
```
GitHub 이슈(한글, ROADMAP 5.2 Contract Test) 등록 → PR 생성(base develop). CI green 확인. dev 머지는 사용자 게이트.

---

## 검증 체크리스트 (스펙 §11 대응)

- [ ] `:carry-order:test` · `:carry-delivery:test` · `:carry-app:test` GREEN
- [ ] UserQueryPort 스모크 IT GREEN(Testcontainers)
- [ ] teeth 뮤테이션 4건 RED 확인 후 원복(working tree clean)
- [ ] CI green
- [ ] 프로덕션 코드 변경 0 (계약/Fake/테스트·build.gradle.kts만)
