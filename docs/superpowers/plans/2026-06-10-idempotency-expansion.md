# 명령측 멱등성 확장 Implementation Plan

> **For agentic workers:** REQUIRED: superpowers:subagent-driven-development 또는 executing-plans로 실행. 체크박스(`- [ ]`) 단계 추적.

**Goal:** `Idempotency-Key` 멱등성을 `Payment.requestPayment`·`Review.createReview`로 확장하고, Redis/InMemory 멱등 메커니즘을 `carry-infra-redis`로 추출해 order/payment/review가 공유.

**Architecture:** 공유 `IdempotencyStore`(carry-infra-redis, prefix 파라미터) + 각 모듈 자체 아웃바운드 포트 + thin adapter 위임 + nullable `StringRedisTemplate`→InMemory fallback 설정. Order는 포트·서비스 무변경(어댑터만 교체).

**Tech Stack:** Kotlin, Spring Boot 3.4, Redis(StringRedisTemplate), JUnit5+AssertJ+mockk, Gradle(JDK21).

**Spec:** `docs/superpowers/specs/2026-06-10-idempotency-expansion-design.md`

**규약:** JDK21(`org.gradle.java.home` 미커밋 유지). BUILD SUCCESSFUL 불신 → `build/test-results/**/*.xml` 수치 검증. 커밋 한국어 본문(`git commit -F`). 브랜치 `feature/idempotency-expansion`.

**파일 맵:**
- 생성(infra): `carry-infra-redis/.../redis/{IdempotencyStore,RedisIdempotencyStore,InMemoryIdempotencyStore}.kt` + 테스트 2 + build.gradle.kts test-deps
- 생성(payment): `application/port/outbound/PaymentIdempotencyPort.kt`, `adapter/outbound/redis/{PaymentIdempotencyAdapter,PaymentIdempotencyConfig}.kt` + 테스트
- 생성(review): 동일 구조
- 수정(payment): `RequestPaymentCommand`(+필드), `PaymentController`(+헤더), `PaymentCommandService`(+3단계), build.gradle.kts(+infra-redis), `PaymentCommandServiceTest`
- 수정(review): `CreateReviewCommand`, `ReviewController`, `ReviewCommandService`, build.gradle.kts, `ReviewCommandServiceTest`
- 수정/삭제(order): `OrderIdempotencyAdapter.kt`(신규), `IdempotencyStoreConfig.kt`(재작성), **삭제** `RedisIdempotencyAdapter.kt`·`InMemoryIdempotencyStore.kt` + 두 테스트(이전됨)

---

## Chunk 1: 공유 멱등 메커니즘 (carry-infra-redis)

### Task 1: build.gradle.kts test 의존성 + IdempotencyStore 인터페이스

**Files:** Modify `carry-infra-redis/build.gradle.kts`; Create `carry-infra-redis/src/main/kotlin/com/carry/infra/redis/IdempotencyStore.kt`

- [ ] **Step 1:** `carry-infra-redis/build.gradle.kts` `dependencies`에 test 의존 추가(타 모듈 버전 일치):
```kotlin
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
```
(junit-jupiter는 루트 subprojects가 전 모듈 제공 — CollectionMappingTest 선례로 확인됨. 빌드 후 미해결이면 `testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")` 추가.)
- [ ] **Step 2:** `IdempotencyStore.kt` 작성:
```kotlin
package com.carry.infra.redis

interface IdempotencyStore {
    fun reserve(key: String): Boolean
    fun findCompletedId(key: String): Long?
    fun complete(key: String, id: Long)
}
```
- [ ] **Step 3:** `./gradlew :carry-infra-redis:compileKotlin` 성공 확인.
- [ ] **Step 4:** 커밋 `feat(infra): 멱등 저장 공유 인터페이스 IdempotencyStore 추가`

### Task 2: InMemoryIdempotencyStore + 테스트 (TDD)

**Files:** Create `carry-infra-redis/.../redis/InMemoryIdempotencyStore.kt`; Test `.../src/test/kotlin/com/carry/infra/redis/InMemoryIdempotencyStoreTest.kt`

- [ ] **Step 1: 실패 테스트**(AssertJ; order의 InMemoryIdempotencyStoreTest 일반화):
```kotlin
package com.carry.infra.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryIdempotencyStoreTest {
    private val sut = InMemoryIdempotencyStore()

    @Test fun `reserve 는 처음 1회만 true`() {
        assertThat(sut.reserve("k")).isTrue()
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test fun `complete 후 findCompletedId 가 결과를 반환한다`() {
        sut.reserve("k"); sut.complete("k", 42L)
        assertThat(sut.findCompletedId("k")).isEqualTo(42L)
    }

    @Test fun `complete 된 키는 다시 reserve 되지 않는다`() {
        sut.reserve("k"); sut.complete("k", 42L)
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test fun `미처리 키의 findCompletedId 는 null`() {
        assertThat(sut.findCompletedId("absent")).isNull()
    }
}
```
- [ ] **Step 2:** `./gradlew :carry-infra-redis:test --tests "*InMemoryIdempotencyStoreTest*"` → 컴파일 실패(RED).
- [ ] **Step 3: 구현**:
```kotlin
package com.carry.infra.redis

import java.util.concurrent.ConcurrentHashMap

/**
 * [IdempotencyStore] 인메모리 fallback(비분산, dev/test). 단일 프로세스 원자성만 보장,
 * 다중 인스턴스·TTL 만료 없음. 분산 환경은 [RedisIdempotencyStore].
 */
class InMemoryIdempotencyStore : IdempotencyStore {
    private val completed = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    override fun reserve(key: String): Boolean {
        if (completed.containsKey(key)) return false
        return pending.add(key)
    }

    override fun findCompletedId(key: String): Long? = completed[key]

    override fun complete(key: String, id: Long) {
        completed[key] = id
        pending.remove(key)
    }
}
```
- [ ] **Step 4:** 동일 명령 → PASS 4건. XML tests=4 failures=0 확인.
- [ ] **Step 5:** 커밋 `feat(infra): InMemoryIdempotencyStore(비분산 fallback) + 테스트`

### Task 3: RedisIdempotencyStore + 테스트 (TDD)

**Files:** Create `.../redis/RedisIdempotencyStore.kt`; Test `.../redis/RedisIdempotencyStoreTest.kt`

- [ ] **Step 1: 실패 테스트**(order의 RedisIdempotencyAdapterTest 일반화 — keyPrefix 명시 전달로 prefix 결합 커버리지 유지):
```kotlin
package com.carry.infra.redis

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class RedisIdempotencyStoreTest {
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redis = mockk<StringRedisTemplate>(relaxed = true)
    private val sut = RedisIdempotencyStore(redis, "idem:test:", Duration.ofSeconds(120), Duration.ofHours(24))

    init { every { redis.opsForValue() } returns valueOps }

    @Test fun `reserve 는 SETNX 성공 시 true`() {
        every { valueOps.setIfAbsent("idem:test:k", "PENDING", Duration.ofSeconds(120)) } returns true
        assertThat(sut.reserve("k")).isTrue()
    }

    @Test fun `reserve 는 키가 이미 있으면 false`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test fun `findCompletedId 는 숫자 문자열을 Long 으로`() {
        every { valueOps.get("idem:test:k") } returns "42"
        assertThat(sut.findCompletedId("k")).isEqualTo(42L)
    }

    @Test fun `findCompletedId 는 PENDING·부재면 null`() {
        every { valueOps.get("idem:test:p") } returns "PENDING"
        every { valueOps.get("idem:test:absent") } returns null
        assertThat(sut.findCompletedId("p")).isNull()
        assertThat(sut.findCompletedId("absent")).isNull()
    }

    @Test fun `complete 는 결과 TTL 로 id 문자열 저장`() {
        sut.complete("k", 42L)
        verify { valueOps.set("idem:test:k", "42", Duration.ofHours(24)) }
    }
}
```
- [ ] **Step 2:** `./gradlew :carry-infra-redis:test --tests "*RedisIdempotencyStoreTest*"` → RED.
- [ ] **Step 3: 구현**:
```kotlin
package com.carry.infra.redis

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [IdempotencyStore] Redis 구현(분산). keyPrefix 로 모듈 간 키공간 분리.
 * 값은 모두 문자열(StringRedisTemplate 전용)이라 JSON 직렬화 모호성 없음.
 * - reserve: SETNX + 짧은 pendingTtl(처리 실패해도 키가 영원히 막히지 않음)
 * - complete: 결과 id 로 교체 + 긴 resultTtl(이후 동일 키 재생)
 */
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String,
    private val pendingTtl: Duration,
    private val resultTtl: Duration,
) : IdempotencyStore {
    override fun reserve(key: String): Boolean =
        redis.opsForValue().setIfAbsent(fullKey(key), PENDING, pendingTtl) == true

    override fun findCompletedId(key: String): Long? =
        redis.opsForValue().get(fullKey(key))?.toLongOrNull()

    override fun complete(key: String, id: Long) {
        redis.opsForValue().set(fullKey(key), id.toString(), resultTtl)
    }

    private fun fullKey(key: String): String = "$keyPrefix$key"

    companion object { private const val PENDING = "PENDING" }
}
```
- [ ] **Step 4:** 동일 명령 → PASS 5건. XML 확인.
- [ ] **Step 5:** 커밋 `feat(infra): RedisIdempotencyStore(prefix 파라미터 분산 구현) + 테스트`

---

## Chunk 2: carry-payment 멱등성

### Task 4: 포트 + 어댑터 + 설정 + 의존성

**Files:** Modify `carry-payment/build.gradle.kts`; Create `application/port/outbound/PaymentIdempotencyPort.kt`, `adapter/outbound/redis/PaymentIdempotencyAdapter.kt`, `adapter/outbound/redis/PaymentIdempotencyConfig.kt`

- [ ] **Step 1:** `carry-payment/build.gradle.kts` dependencies에 `implementation(project(":carry-infra-redis"))` 추가.
- [ ] **Step 2:** 포트:
```kotlin
package com.carry.payment.application.port.outbound

interface PaymentIdempotencyPort {
    fun reserve(key: String): Boolean
    fun findCompletedPaymentId(key: String): Long?
    fun complete(key: String, paymentId: Long)
}
```
- [ ] **Step 3:** 어댑터(공유 store 위임):
```kotlin
package com.carry.payment.adapter.outbound.redis

import com.carry.infra.redis.IdempotencyStore
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort

class PaymentIdempotencyAdapter(private val store: IdempotencyStore) : PaymentIdempotencyPort {
    override fun reserve(key: String) = store.reserve(key)
    override fun findCompletedPaymentId(key: String) = store.findCompletedId(key)
    override fun complete(key: String, paymentId: Long) = store.complete(key, paymentId)
}
```
- [ ] **Step 4:** 설정(order IdempotencyStoreConfig 패턴, nullable fallback):
```kotlin
package com.carry.payment.adapter.outbound.redis

import com.carry.infra.redis.InMemoryIdempotencyStore
import com.carry.infra.redis.RedisIdempotencyStore
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

@Configuration
class PaymentIdempotencyConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun paymentIdempotencyPort(
        @Autowired(required = false) stringRedisTemplate: StringRedisTemplate?,
        @Value("\${carry.idempotency.pending-ttl-seconds:120}") pendingTtlSeconds: Long,
        @Value("\${carry.idempotency.result-ttl-hours:24}") resultTtlHours: Long,
    ): PaymentIdempotencyPort {
        val store = if (stringRedisTemplate != null) {
            RedisIdempotencyStore(stringRedisTemplate, "idem:payment:request:",
                Duration.ofSeconds(pendingTtlSeconds), Duration.ofHours(resultTtlHours))
        } else {
            log.warn("StringRedisTemplate 부재 — PaymentIdempotency 인메모리(비분산, dev/test)")
            InMemoryIdempotencyStore()
        }
        return PaymentIdempotencyAdapter(store)
    }
}
```
- [ ] **Step 5:** `./gradlew :carry-payment:compileKotlin` 성공.
- [ ] **Step 6:** 커밋 `feat(payment): 멱등성 포트·어댑터·설정(공유 store 위임)`

### Task 5: 명령 필드 + 컨트롤러 헤더 + 서비스 3단계 (TDD)

**Files:** Modify `PaymentCommandUseCase.kt`(RequestPaymentCommand), `PaymentController.kt`, `PaymentCommandService.kt`, `PaymentCommandServiceTest.kt`

- [ ] **Step 1: 실패 테스트** — `PaymentCommandServiceTest`에 Idempotency `@Nested` 추가(OrderCommandServiceTest 템플릿). SUT 생성자에 `idempotencyPort = mockk<PaymentIdempotencyPort>(relaxed=true)` 추가(기존 테스트도 이 인자 필요). 3케이스:
  - 완료 키 재요청 → `findCompletedPaymentId` 반환 id로 `findById` 재생, PG/save 미호출(`gateway.requestPayment` 0회).
  - reserve 실패 → `BusinessException(IDEMPOTENT_REQUEST_IN_PROGRESS)`.
  - 신규 키 → reserve 후 처리, `complete(key, paymentId)` 호출 검증.
```kotlin
// 예시 골격(기존 fixture/mock 명에 맞춰 조정)
@Nested
inner class Idempotency {
    @Test fun `완료된 키는 PG·청구서 조회 없이 기존 결제를 재생한다`() {
        every { idempotencyPort.findCompletedPaymentId("k") } returns 7L
        every { paymentPersistencePort.findById(7L) } returns existingPayment
        val result = sut.requestPayment(command.copy(idempotencyKey = "k"))
        assertThat(result.id).isEqualTo(7L)
        // 최상단 재생 = 본문 진입 0(reserve-before-PG 보장의 대칭 검증)
        verify(exactly = 0) { idempotencyPort.reserve(any()) }
        verify(exactly = 0) { invoicePersistencePort.findByOrderId(any()) }
        verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }
    }
    @Test fun `진행 중 키는 409`() {
        every { idempotencyPort.findCompletedPaymentId("k") } returns null
        every { idempotencyPort.reserve("k") } returns false
        assertThatThrownBy { sut.requestPayment(command.copy(idempotencyKey = "k")) }
            .isInstanceOf(BusinessException::class.java)
        verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }  // reserve 실패 시 PG 미호출
    }
    @Test fun `신규 키 성공 시 reserve 후 처리하고 complete 한다`() {
        every { idempotencyPort.findCompletedPaymentId("k") } returns null
        every { idempotencyPort.reserve("k") } returns true
        // ... 기존 성공 경로 stub(PG success) ...
        sut.requestPayment(command.copy(idempotencyKey = "k"))
        verify { idempotencyPort.complete("k", any()) }
    }
    @Test fun `PG 실패(정상 반환)도 결과를 complete 한다`() {
        // 동일 키 재시도는 그 FAILED 결과를 재생(정상 멱등 의미). 진짜 재시도는 새 키.
        every { idempotencyPort.findCompletedPaymentId("k") } returns null
        every { idempotencyPort.reserve("k") } returns true
        // ... PG가 success=false 반환하도록 stub(예외 아님) ...
        val result = sut.requestPayment(command.copy(idempotencyKey = "k"))
        assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        verify { idempotencyPort.complete("k", any()) }
    }
}
```
> **complete 의미 결정(명시)**: `complete`는 정상 반환하는 **두 분기(성공·PG실패)** 모두에서 호출한다 — 같은 키 = 같은 작업 결과(FAILED 포함) 재생. 반면 PG **예외**(CB OPEN 등) 전파 시엔 `@Transactional` 롤백 + `complete` 미호출 → `pendingTtl` 만료 후 재시도 허용. 이 둘은 서로 다른 실패 모드이며 의도된 동작이다.
- [ ] **Step 2:** `RequestPaymentCommand`에 `val idempotencyKey: String? = null` 추가(`PaymentCommandUseCase.kt`).
- [ ] **Step 3:** `./gradlew :carry-payment:test --tests "*PaymentCommandServiceTest*"` → RED(컴파일/단언 실패).
- [ ] **Step 4: 구현** — `PaymentCommandService`:
  - 생성자에 `private val idempotencyPort: PaymentIdempotencyPort` 추가(clock 앞 등 적절 위치). import `BusinessException`·`ErrorCode`.
  - `requestPayment` 최상단:
```kotlin
val key = command.idempotencyKey
if (key != null) {
    idempotencyPort.findCompletedPaymentId(key)?.let { return findPayment(it) }
    if (!idempotencyPort.reserve(key)) {
        throw BusinessException(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS,
            "동일한 Idempotency-Key 요청이 이미 진행 중입니다: $key")
    }
}
```
  - 성공·실패 두 분기 각각 `return saved` 직전에:
```kotlin
key?.let { idempotencyPort.complete(it, saved.id!!) }
```
  - private helper:
```kotlin
private fun findPayment(id: Long): Payment =
    paymentPersistencePort.findById(id) ?: throw PaymentNotFoundException("id=$id")
```
- [ ] **Step 5:** `PaymentController.requestPayment`에 `@RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?` 파라미터 추가, command에 `idempotencyKey = idempotencyKey` 전달. `@Operation` description에 "동일 Idempotency-Key 재요청은 기존 결과 재생; 진짜 재시도는 새 키 사용" 한 줄 추가.
- [ ] **Step 6:** `./gradlew :carry-payment:test` 전체 GREEN. XML tests/failures 확인(기존 + 신규 3).
- [ ] **Step 7:** 커밋 `feat(payment): requestPayment 멱등성(Idempotency-Key) 적용`

### Chunk 2 게이트
- [ ] `./gradlew :carry-payment:test` 전체 GREEN(XML).

---

## Chunk 3: carry-review 멱등성

### Task 6: 포트 + 어댑터 + 설정 + 의존성

**Files:** Modify `carry-review/build.gradle.kts`; Create `application/port/outbound/ReviewIdempotencyPort.kt`, `adapter/outbound/redis/{ReviewIdempotencyAdapter,ReviewIdempotencyConfig}.kt`

- [ ] **Step 1:** `carry-review/build.gradle.kts`에 `implementation(project(":carry-infra-redis"))` 추가.
- [ ] **Step 2~4:** Task 4와 동일 구조로 `ReviewIdempotencyPort`(`findCompletedReviewId`), `ReviewIdempotencyAdapter`(store 위임), `ReviewIdempotencyConfig`(prefix `idem:review:create:`). 패키지는 `com.carry.review.*`.
- [ ] **Step 5:** `./gradlew :carry-review:compileKotlin` 성공.
- [ ] **Step 6:** 커밋 `feat(review): 멱등성 포트·어댑터·설정(공유 store 위임)`

### Task 7: 명령 필드 + 컨트롤러 헤더 + 서비스 3단계 (TDD)

**Files:** Modify `ReviewCommandUseCase.kt`(CreateReviewCommand), `ReviewController.kt`, `ReviewCommandService.kt`, `ReviewCommandServiceTest.kt`

- [ ] **Step 1: 실패 테스트** — `ReviewCommandServiceTest`에 Idempotency `@Nested` 3케이스(Task 5 골격 미러, `findCompletedReviewId`/`findById`/save·publish 검증). SUT 생성자에 `idempotencyPort = mockk<ReviewIdempotencyPort>(relaxed=true)` 추가.
- [ ] **Step 2:** `CreateReviewCommand`에 `val idempotencyKey: String? = null` 추가.
- [ ] **Step 3:** `:carry-review:test --tests "*ReviewCommandServiceTest*"` → RED.
- [ ] **Step 4: 구현** — `ReviewCommandService`:
  - 생성자에 `private val idempotencyPort: ReviewIdempotencyPort` 추가. import BusinessException·ErrorCode.
  - `createReview` 최상단 3단계(findCompletedReviewId→재생 / reserve→409), save 직후 `key?.let { idempotencyPort.complete(it, saved.id!!) }`.
  - helper `findReview(id) = reviewPersistencePort.findById(id) ?: throw ReviewNotFoundException(id)`.
- [ ] **Step 5:** `ReviewController.createReview`에 `@RequestHeader(value="Idempotency-Key", required=false) idempotencyKey: String?` + command 전달 + `@Operation` 설명 보강.
- [ ] **Step 6:** `./gradlew :carry-review:test` 전체 GREEN(XML, 기존 + 신규 3).
- [ ] **Step 7:** 커밋 `feat(review): createReview 멱등성(Idempotency-Key) 적용`

### Chunk 3 게이트
- [ ] `./gradlew :carry-review:test` 전체 GREEN(XML).

---

## Chunk 4: carry-order 마이그레이션 (행동 보존)

> 이 chunk는 payment/review와 독립. 문제 시 분리 가능(그 경우 order는 기존 자체 어댑터 유지).

### Task 8: OrderIdempotencyAdapter + 설정 재작성 + 구 클래스/테스트 삭제

**Files:** Create `carry-order/.../adapter/outbound/redis/OrderIdempotencyAdapter.kt`; Modify `IdempotencyStoreConfig.kt`; Delete `RedisIdempotencyAdapter.kt`·`InMemoryIdempotencyStore.kt`(order) + `RedisIdempotencyAdapterTest.kt`·`InMemoryIdempotencyStoreTest.kt`(order, infra-redis로 이전 완료)

- [ ] **Step 1:** `OrderIdempotencyAdapter`(order의 `IdempotencyPort` 만족, 공유 store 위임):
```kotlin
package com.carry.order.adapter.outbound.redis

import com.carry.infra.redis.IdempotencyStore
import com.carry.order.application.port.outbound.IdempotencyPort

class OrderIdempotencyAdapter(private val store: IdempotencyStore) : IdempotencyPort {
    override fun reserve(key: String) = store.reserve(key)
    override fun findCompletedOrderId(key: String) = store.findCompletedId(key)
    override fun complete(key: String, orderId: Long) = store.complete(key, orderId)
}
```
- [ ] **Step 2:** `IdempotencyStoreConfig.kt` 재작성 — `@Bean idempotencyPort(...)`가 공유 store(prefix `idem:order:create:`) 생성 후 `OrderIdempotencyAdapter`로 래핑(payment 설정과 동형). import를 `com.carry.infra.redis.*`로.
- [ ] **Step 3:** order의 구 `RedisIdempotencyAdapter.kt`·`InMemoryIdempotencyStore.kt`와 그 테스트 2개 삭제(`git rm`). (로직·테스트는 Chunk 1에서 infra-redis로 일반화 이전됨.)
- [ ] **Step 4:** `./gradlew :carry-order:test` 전체 GREEN — **기존 OrderCommandServiceTest 멱등 3건 무변경 통과**가 행동 보존 증거. XML 확인.
- [ ] **Step 5:** 커밋 `refactor(order): 멱등 메커니즘을 공유 IdempotencyStore로 위임(행동 보존)`

---

## Chunk 5: 전체 검증 + PR

### Task 9: 전체 빌드/테스트
- [ ] **Step 1:** `./gradlew :carry-infra-redis:test :carry-order:test :carry-payment:test :carry-review:test :carry-app:test`
- [ ] **Step 2:** 각 모듈 `build/test-results/**/*.xml` tests>0·failures=0·errors=0 **수치 확인**.
- [ ] **Step 3:** `git status`로 의도 외 변경(`gradle.properties`·`ROADMAP.md`) 미스테이징 확인.

### Task 10: PR + 자율 머지
- [ ] **Step 1:** `git push -u origin feature/idempotency-expansion`.
- [ ] **Step 2:** `gh pr create --base develop` 한국어 본문(스펙/플랜 링크, 변경 요약, 비범위, reserve-before-PG·complete-on-failed 의미 명시).
- [ ] **Step 3:** CI GREEN 확인 후 `gh pr merge <n> --merge --delete-branch`. develop 동기화.
- [ ] **Step 4:** 메모리 갱신([[carry-platform-known-debts]] "명령측 멱등성 확장" 잔여→해소, roadmap-progress).

---

## 적용 순서 근거
Chunk 1(공유 메커니즘)이 2·3·4의 토대. payment·review는 서로 독립이라 순차. order 마이그레이션은 payment/review와 무의존이라 마지막(리스크 격리). 모든 멱등 적용은 키 미제공 시 기존 동작 보존(하위호환).
