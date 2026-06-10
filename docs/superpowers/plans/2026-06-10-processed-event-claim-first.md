# ProcessedEvent claim-first 멱등 소비자 하드닝 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kafka 소비자측 멱등 관문(`EventConsumerSupport.processIfNotDuplicate`)을 process-then-mark에서 claim-first(네이티브 `ON CONFLICT` INSERT 선점 → block)로 전환해, 동시 중복 배달에서도 부수효과(block)가 정확히 1회 실행되도록(block-at-most-once) 보장한다.

**Architecture:** `ProcessedEventRepository`에 원자적 `claim()` 네이티브 메서드(`INSERT ... ON CONFLICT (id) DO NOTHING`, 영향 행수 반환)를 추가한다. `processIfNotDuplicate`는 `claim()`을 block보다 **먼저** 호출해 1행이면 처리·0행이면 skip하며, `existsById` 선체크와 끝의 `save()`를 제거한다. block이 던지면 `@Transactional`이 claim 행까지 롤백해 at-least-once 재처리를 보존한다. 프로덕션 소비자 6종은 시그니처 불변이라 무변경.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA(`@Modifying @Query nativeQuery`), PostgreSQL(`ON CONFLICT`), JUnit5, mockk, AssertJ, Testcontainers, Gradle(JDK 21 — `org.gradle.java.home=C:/Users/Eisen/.jdks/ms-21.0.7` 미커밋 `gradle.properties`).

**Spec:** `docs/superpowers/specs/2026-06-10-processed-event-claim-first-design.md`

---

## 사전 환경 메모 (실행자 필독)

- **JDK 21 필수.** 머신 기본 JDK가 Java 25라 Gradle 8.12.1이 안 뜬다. `carry-platform/gradle.properties`에 `org.gradle.java.home=C:/Users/Eisen/.jdks/ms-21.0.7` 줄이 있는지 확인(미커밋, 스테이징 금지).
- **테스트 통과 검증은 JUnit XML로.** `BUILD SUCCESSFUL`만으로 믿지 말 것. `<module>/build/test-results/test/TEST-*.xml`의 `tests`/`failures`/`errors` 카운트와 `<testcase>` 노드를 확인. (Gradle은 0개 매칭에도 BUILD SUCCESSFUL을 낸다.)
- **명령 종료코드 확인이 필요하면** `| tail` 대신 `; echo "EXIT=$?"`를 쓴다(파이프가 Gradle 종료코드를 가린다).
- **커밋 메시지는 한국어 본문 + 영어 conventional prefix**, 끝에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. 본문 있는 메시지는 `git commit -F`(heredoc 또는 파일)로 전달(PowerShell 인자 분해 회피).
- **스테이징 제외 파일:** `gradle.properties`(M), 루트 `ROADMAP.md`(??). 각 태스크에서 변경 파일만 명시적으로 `git add`.
- **PostgreSQL 한정 구문(`ON CONFLICT`)** 사용. 단위 테스트는 mockk라 무관, 통합은 Testcontainers PostgreSQL이라 안전.

---

## File Structure

| 파일 | 책임 | 변경 |
|---|---|---|
| `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/ProcessedEventRepository.kt` | 멱등 마커 영속 + 원자적 선점 | `claim()` 네이티브 메서드 추가 |
| `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt` | 소비자 멱등 관문(claim → block) | claim-first 재배치, `existsById`/`save` 제거 |
| `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupportTest.kt` | 관문 제어 흐름 단위(L1) | `claim` 기반 재작성 + 호출 순서 검증 |
| `carry-order/src/test/kotlin/com/carry/order/adapter/inbound/kafka/OrderEventConsumerIdempotencyTest.kt` | dedup 키 배선 단위(L2) | fake 저장소를 `claim()` 기반으로 재작성 |
| `carry-app/src/test/kotlin/com/carry/app/idempotency/ConsumerIdempotencyIntegrationTest.kt` | 실DB/동시성 통합(L1) | 동시 테스트 `marker==1`로 강화 + 주석 갱신 |
| `ProcessedEvent.kt` | 멱등 마커 엔티티 | **무변경** |
| 프로덕션 소비자 6종 | — | **무변경** |

**기존 코드 참조 (변경 전 상태):**

```kotlin
// ProcessedEventRepository.kt (현재 전체)
interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String>

// EventConsumerSupport.processIfNotDuplicate (현재)
@Transactional
fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
    if (processedEventRepository.existsById(eventId)) {
        log.debug("Skipping duplicate event: {}", eventId)
        return
    }
    val span = tracer.nextSpan().name("consume.${eventType ?: "unknown"}")
    traceId?.let { span.tag("saga.traceId", it) }
    span.start()
    try {
        tracer.withSpan(span).use {
            traceId?.let { MDC.put("saga.traceId", it) }
            block()
        }
    } catch (e: Exception) {
        span.error(e)
        throw e
    } finally {
        MDC.remove("saga.traceId")
        span.end()
    }
    processedEventRepository.save(ProcessedEvent(id = eventId))
}
```

`processed_events` 스키마 (진실 출처 = `carry-app/src/main/resources/db/migration/V1__init_outbox_tables.sql`):
```sql
CREATE TABLE IF NOT EXISTS processed_events (
    id              VARCHAR(100) PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Chunk 1: claim-first 프로덕션 전환 + 단위 테스트

### Task 1: `ProcessedEventRepository.claim()` 네이티브 선점 메서드

**Files:**
- Modify: `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/ProcessedEventRepository.kt`

이 메서드는 통합 테스트(Task 4)에서 실DB로 검증된다. 단위(mockk) 레이어에서는 stub 대상일 뿐이라, 여기서는 **메서드 시그니처/쿼리 추가만** 하고 컴파일 통과로 마무리한다(별도 단위 테스트 없음 — 네이티브 SQL은 mockk로 의미 검증 불가).

- [ ] **Step 1: `claim()` 메서드 추가**

`ProcessedEventRepository.kt` 전체를 아래로 교체:

```kotlin
package com.carry.infra.kafka.consumer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String> {

    /**
     * 이벤트를 원자적으로 선점(claim)한다. 처리 시작 전에 호출한다.
     *
     * `INSERT ... ON CONFLICT (id) DO NOTHING`이라 동시 중복에서도 정확히 한 트랜잭션만
     * 1행을 삽입한다. 반환값 = 영향 행수: **1 = 선점 성공(처리 진행)**, **0 = 이미 처리됨(skip)**.
     *
     * PostgreSQL 한정 구문. 호출자의 @Transactional 경계 안에서 실행되며, block 실패로
     * 트랜잭션이 롤백되면 이 INSERT도 함께 롤백되어 재처리가 가능하다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO processed_events (id, processed_at) VALUES (:id, :processedAt) " +
            "ON CONFLICT (id) DO NOTHING",
        nativeQuery = true,
    )
    fun claim(@Param("id") id: String, @Param("processedAt") processedAt: Instant): Int
}
```

> 주의: `@Modifying`의 `clearAutomatically`/`flushAutomatically`는 **기본값(false) 유지**. 이후 `block()`이 같은 영속성 컨텍스트에서 ORM 쓰기를 하므로 컨텍스트를 비우면 안 된다(`clearAutomatically=true` 금지).

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :carry-infra-kafka:compileKotlin ; echo "EXIT=$?"`
Expected: `EXIT=0` (BUILD SUCCESSFUL)

- [ ] **Step 3: 커밋**

```bash
git add carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/ProcessedEventRepository.kt
git commit -F - <<'EOF'
feat(idempotency): ProcessedEventRepository.claim() 원자적 선점 메서드 추가

INSERT ... ON CONFLICT (id) DO NOTHING 네이티브 쿼리로 동시 중복에서도
한 트랜잭션만 1행 삽입. 반환 영향행수로 선점 성공(1)/중복(0) 판별.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: `EventConsumerSupportTest` 단위 테스트를 claim 기반으로 재작성 (RED)

**Files:**
- Modify: `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupportTest.kt`

claim-first 동작을 먼저 테스트로 고정한다. 프로덕션(`EventConsumerSupport`)은 아직 옛 흐름이라 이 테스트는 **실패해야 한다**(RED). 롤백 불변식은 트랜잭션 관심사라 단위에서 빠지고 통합(Task 4)으로 이전된다.

- [ ] **Step 1: 테스트 파일 전체 교체**

```kotlin
package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 멱등성 회귀 — `EventConsumerSupport.processIfNotDuplicate`의 제어 흐름 단위 테스트(L1).
 *
 * 보호 불변식: claim-first. 동일 eventId가 2회 이상 들어와도 부수효과(block)는 최대 1회.
 * 여기서는 DB 없이 분기·순서 로직만 결정적으로 고정한다(claim 성공/중복/예외 전파, claim이
 * block보다 먼저 호출됨). 실영속/동시성/롤백은 carry-app 통합테스트에서 검증.
 */
class EventConsumerSupportTest {

    private val processedEventRepository = mockk<ProcessedEventRepository>()
    private val tracer = mockk<Tracer>(relaxed = true)
    private val support = EventConsumerSupport(processedEventRepository, tracer)

    @Test
    fun `claim에 성공하면(1) block을 실행한다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 1

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(1)
        verify(exactly = 1) { processedEventRepository.claim(eq("evt-1"), any<Instant>()) }
    }

    @Test
    fun `claim이 중복이면(0) block을 실행하지 않는다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 0

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(0)
    }

    @Test
    fun `claim은 block보다 먼저 호출된다 (claim-first)`() {
        val calls = mutableListOf<String>()
        // claim의 answers에서 호출 시점을 calls에 기록 → block의 기록과 순서를 비교한다.
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } answers {
            calls.add("claim"); 1
        }

        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { calls.add("block") }

        // 실제 실행 순서가 claim → block 임을 단언(claim-first의 핵심).
        assertThat(calls).containsExactly("claim", "block")
    }

    @Test
    fun `block이 예외를 던지면 전파된다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 1

        // 롤백(claim 행 제거)은 @Transactional/실DB 관심사 → 통합 테스트에서 검증.
        // 단위에서는 예외 전파만 단언한다.
        assertThatThrownBy {
            support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") {
                throw IllegalStateException("downstream failure")
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
```

> 순서 검증 노트: `claim`의 `answers` 블록에서 호출 시점을 공유 `calls` 리스트에 기록하고, block도 기록하게 해 `containsExactly("claim", "block")`로 **실제 실행 순서**를 단언한다(verifyOrder는 람다 실행을 끼워 검증 못 하므로 사용 안 함). 이 테스트가 claim-first 순서를 직접 증명한다.

- [ ] **Step 2: RED 확인**

Run: `./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.consumer.EventConsumerSupportTest" ; echo "EXIT=$?"`
Expected: **테스트 실패**(`EXIT` ≠ 0). Task 1에서 `claim()`을 이미 추가했으므로 **컴파일은 통과**한다. 실패 사유는 런타임: 프로덕션 `processIfNotDuplicate`가 아직 옛 흐름이라 `claim` 대신 stub 안 된 `existsById`를 호출 → mockk가 `MockKException`을 던져 4개 테스트가 실패한다(진짜 RED). (커밋 없음 — Task 3에서 GREEN과 함께 커밋)

---

### Task 3: `EventConsumerSupport` claim-first 재배치 (GREEN)

**Files:**
- Modify: `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt`

- [ ] **Step 1: `processIfNotDuplicate` 본문 교체**

`existsById` 선체크와 끝의 `save()`를 제거하고 맨 앞에 `claim()` 가드를 둔다:

```kotlin
package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class EventConsumerSupport(
    private val processedEventRepository: ProcessedEventRepository,
    private val tracer: Tracer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
        // claim-first: 처리 시작 전에 원자적으로 선점한다. 0행이면 이미 처리됨(중복) → skip.
        // 동시 중복에서도 ON CONFLICT가 한 트랜잭션만 통과시켜 block은 최대 1회 실행된다.
        if (processedEventRepository.claim(eventId, Instant.now()) == 0) {
            log.debug("Skipping duplicate event: {}", eventId)
            return
        }

        val span = tracer.nextSpan().name("consume.${eventType ?: "unknown"}")
        traceId?.let { span.tag("saga.traceId", it) }
        span.start()
        try {
            tracer.withSpan(span).use {
                traceId?.let { MDC.put("saga.traceId", it) }
                block()
            }
        } catch (e: Exception) {
            // block 실패 → 예외 전파 → @Transactional 롤백 → claim 행도 롤백 → 재처리 가능(at-least-once)
            span.error(e)
            throw e
        } finally {
            MDC.remove("saga.traceId")
            span.end()
        }
    }
}
```

- [ ] **Step 2: Task 2 단위 테스트 GREEN 확인**

Run: `./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.consumer.EventConsumerSupportTest" ; echo "EXIT=$?"`
Expected: `EXIT=0`. XML 확인: `carry-infra-kafka/build/test-results/test/TEST-com.carry.infra.kafka.consumer.EventConsumerSupportTest.xml`에 `tests="4" failures="0" errors="0"`.

- [ ] **Step 3: 모듈 전체 테스트로 회귀 없음 확인**

Run: `./gradlew :carry-infra-kafka:test ; echo "EXIT=$?"`
Expected: `EXIT=0`, 전체 GREEN.

- [ ] **Step 4: 커밋 (테스트 + 프로덕션 함께)**

```bash
git add carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt \
        carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupportTest.kt
git commit -F - <<'EOF'
feat(idempotency): processIfNotDuplicate claim-first 재배치

existsById 선체크와 끝의 save()를 제거하고, 맨 앞에서 claim()으로 선점한다.
0행(중복)이면 skip, 1행이면 같은 tx에서 block 실행. block 실패 시 claim 행이
함께 롤백돼 at-least-once 재처리 유지. 단위 테스트를 claim 기반으로 재작성.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Chunk 2: 컨슈머 배선 fake 수정 + 통합 동시성 강화

### Task 4: `OrderEventConsumerIdempotencyTest` fake를 claim 기반으로 재작성

**Files:**
- Modify: `carry-order/src/test/kotlin/com/carry/order/adapter/inbound/kafka/OrderEventConsumerIdempotencyTest.kt`

이 테스트는 **실제** `EventConsumerSupport`를 구동하므로, claim-first 전환 후엔 fake가 `claim()`을 백킹해야 한다. 미수정 시 unstubbed `claim()`로 mockk가 던져 기존 GREEN이 깨진다.

- [ ] **Step 1: import 정리 + `inMemoryEventConsumerSupport` fake 교체**

먼저 import 두 곳을 정확히 손본다(ktlint ASCII 정렬 유지):
- **제거**: `import com.carry.infra.kafka.consumer.ProcessedEvent` (claim 기반 fake에선 미사용)
- **추가**: `import java.time.Instant` — 정렬상 `import io.mockk.verify`와 `import org.apache.kafka.clients.consumer.ConsumerRecord` 사이에 둔다(com → io → java → org 순).

그 다음 fake 메서드만 아래로 교체(나머지 테스트 본문·단언은 그대로):

```kotlin
    /** claim()이 실제로 동작하는 in-memory fake (키 = eventId). 최초 1, 이후 0. */
    private fun inMemoryEventConsumerSupport(): EventConsumerSupport {
        val processedIds = mutableSetOf<String>()
        val repo = mockk<ProcessedEventRepository>()
        every { repo.claim(any<String>(), any<Instant>()) } answers {
            if (processedIds.add(firstArg<String>())) 1 else 0
        }
        return EventConsumerSupport(repo, mockk<Tracer>(relaxed = true))
    }
```

> `MutableSet.add`는 새 원소면 true(→1), 이미 있으면 false(→0)를 반환해 `ON CONFLICT` 의미와 정확히 일치한다.

- [ ] **Step 2: 테스트 GREEN 확인**

Run: `./gradlew :carry-order:test --tests "com.carry.order.adapter.inbound.kafka.OrderEventConsumerIdempotencyTest" ; echo "EXIT=$?"`
Expected: `EXIT=0`. XML: `carry-order/build/test-results/test/TEST-...OrderEventConsumerIdempotencyTest.xml`에 `tests="1" failures="0"`.

- [ ] **Step 3: carry-order 모듈 전체 회귀 확인**

Run: `./gradlew :carry-order:test ; echo "EXIT=$?"`
Expected: `EXIT=0`.

- [ ] **Step 4: 커밋**

```bash
git add carry-order/src/test/kotlin/com/carry/order/adapter/inbound/kafka/OrderEventConsumerIdempotencyTest.kt
git commit -F - <<'EOF'
test(idempotency): OrderEventConsumer 멱등 fake를 claim 기반으로 재작성

claim-first 전환으로 EventConsumerSupport가 claim()을 호출하므로, 컨슈머 배선
테스트의 in-memory fake도 Set.add 기반 claim(최초 1/이후 0)으로 교체. 단언 불변.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 5: 통합 동시 테스트를 `marker == 1`로 강화 (RED → GREEN)

**Files:**
- Modify: `carry-app/src/test/kotlin/com/carry/app/idempotency/ConsumerIdempotencyIntegrationTest.kt`

claim-first 프로덕션 변경(Chunk 1)은 이미 머지됐으므로, 이 강화 테스트는 **바로 GREEN**이어야 한다. teeth 증명을 위해 Step 3에서 프로덕션을 일시 원복해 RED를 확인한 뒤 되돌린다.

- [ ] **Step 1: 동시 테스트 단언 강화 + 주석 갱신**

`같은 이벤트를 8개 스레드가 동시에 처리해도...` 테스트의 단언 블록(현재 `assertThat(processed)...isEqualTo(1)` + `assertThat(marker).isBetween(1, threads)`)을 아래로 교체:

```kotlin
        // claim-first: 동시 중복에서도 부수효과(block)는 정확히 1회만 커밋된다(block-at-most-once).
        assertThat(marker)
            .`as`("marker=%d processed=%d ok=%d failed=%d", marker, processed, ok, failed)
            .isEqualTo(1)
        // 처리 마킹도 정확히 1행(DB PK + ON CONFLICT가 강제).
        assertThat(processed).isEqualTo(1)
        // 패자 스레드도 예외 없이 깨끗이 skip(0행 claim) → 전부 성공.
        assertThat(ok).isEqualTo(threads)
        assertThat(failed).isEqualTo(0)
```

그리고 이 테스트의 KDoc 블록(현재 "⚠️ 단언 범위 주의: ... block(부수효과)이 정확히 1회는 진짜 동시성에선 보장되지 않는다 ..." 단락)을 아래로 교체:

```kotlin
    /**
     * 진짜 동시 중복 배달에서도 **block(부수효과)이 정확히 1회**만 커밋됨을 증명한다(block-at-most-once).
     *
     * claim-first(`processIfNotDuplicate`가 block 이전에 `ProcessedEventRepository.claim()` =
     * `INSERT ... ON CONFLICT (id) DO NOTHING`으로 선점)이므로, 8스레드가 동시에 같은 eventId를
     * 밀어넣어도 Postgres가 한 트랜잭션만 1행 삽입을 통과시키고 나머지는 0행(skip)을 받는다.
     * 따라서 marker(부수효과)·processed(마킹) 모두 정확히 1, 패자도 예외 없이 종료한다.
     * 실패 롤백·순차 dedup은 위 두 테스트가 보장한다.
     */
```

또한 **실패 롤백 테스트**(`block이 실패하면 marker와 ProcessedEvent가 함께 롤백된다`)의 끝
단언(`assertThat(processedCount(eventId)).isEqualTo(0)`) 위에 한 줄 주석을 추가해, claim-first 하에선
이 단언이 "block 이전에 일어난 claim INSERT 행 자체가 tx와 함께 롤백됨"을 증명하는 더 강한 경로임을 명시:

```kotlin
        // claim-first: claim INSERT가 block 이전에 일어나므로, 이 0 단언은 claim 행 자체가
        // tx 롤백으로 사라짐을 증명한다(재처리 가능).
        assertThat(markerCount(eventId)).isEqualTo(0)
        assertThat(processedCount(eventId)).isEqualTo(0)
```

- [ ] **Step 2: 강화 테스트 GREEN 확인 (claim-first 프로덕션 위에서)**

Run: `./gradlew :carry-app:test --tests "com.carry.app.idempotency.ConsumerIdempotencyIntegrationTest" ; echo "EXIT=$?"`
Expected: `EXIT=0`. XML: `carry-app/build/test-results/test/TEST-...ConsumerIdempotencyIntegrationTest.xml`에 `tests="3" failures="0" errors="0"`, `system-out`에 Testcontainers(Ryuk/postgres) 기동 로그.

- [ ] **Step 3: 뮤테이션(teeth) 검증 — 프로덕션 일시 원복으로 RED 확인**

claim-first 순서가 진짜 teeth임을 증명한다. `EventConsumerSupport.processIfNotDuplicate` 본문을 **임시로** 아래 process-then-mark 버전으로 정확히 교체한다(claim 가드 제거 + claim을 block 뒤로 이동). **절대 커밋하지 말 것:**

```kotlin
    @Transactional
    fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
        // [임시 뮤테이션 — 절대 커밋 금지] claim 가드 제거 + claim을 block 뒤로 이동(process-then-mark 복원)
        val span = tracer.nextSpan().name("consume.${eventType ?: "unknown"}")
        traceId?.let { span.tag("saga.traceId", it) }
        span.start()
        try {
            tracer.withSpan(span).use {
                traceId?.let { MDC.put("saga.traceId", it) }
                block()
            }
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            MDC.remove("saga.traceId")
            span.end()
        }
        processedEventRepository.claim(eventId, Instant.now())
    }
```

이 변형은 모든 스레드가 가드 없이 block을 실행하고, `claim()`은 `ON CONFLICT DO NOTHING`이라 던지지 않으므로 이미 실행된 부수효과가 잔존 → `marker > 1`이 관측된다.

Run: `./gradlew :carry-app:test --tests "com.carry.app.idempotency.ConsumerIdempotencyIntegrationTest" --rerun-tasks ; echo "EXIT=$?"`
Expected: **FAIL** — `marker == 1` 단언이 `marker > 1`로 깨진다(동시 8스레드가 block을 여러 번 실행). 이로써 강화 단언이 순서 회귀를 잡는 teeth임이 증명됨.

- [ ] **Step 4: 프로덕션 원복**

`EventConsumerSupport.processIfNotDuplicate`를 Task 3의 claim-first 버전으로 정확히 되돌린다.

Run: `git diff carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt ; echo "EXIT=$?"`
Expected: **출력 없음**(Task 3 커밋과 동일 = 뮤테이션 흔적 0).

- [ ] **Step 5: 강화 테스트 재-GREEN 확인 + flaky 0 확인**

Run: `./gradlew :carry-app:test --tests "com.carry.app.idempotency.ConsumerIdempotencyIntegrationTest" --rerun-tasks ; echo "EXIT=$?"`
Expected: `EXIT=0`. (동시 테스트라 flaky 여부 확인 — 한 번 더 `--rerun-tasks`로 재실행해 GREEN 재현.)

- [ ] **Step 6: 커밋**

```bash
git add carry-app/src/test/kotlin/com/carry/app/idempotency/ConsumerIdempotencyIntegrationTest.kt
git commit -F - <<'EOF'
test(idempotency): 동시 멱등 통합 테스트를 marker==1로 강화

claim-first 하드닝으로 block-at-most-once가 보장되므로, 동시 8스레드 테스트의
부수효과 단언을 [1,threads] → 정확히 1로 강화(processed==1·ok==threads 동반).
KDoc의 "동시성 미보장" 경고를 "claim-first로 보장됨"으로 갱신. 프로덕션 일시
원복으로 marker>1 RED 뮤테이션 검증 후 원복.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Chunk 3: 전체 검증 + PR

### Task 6: 영향 모듈 전체 테스트 + 라이브 스모크 판단

**Files:** 없음(검증 전용)

- [ ] **Step 1: 세 모듈 전체 테스트**

Run: `./gradlew :carry-infra-kafka:test :carry-order:test :carry-app:test ; echo "EXIT=$?"`
Expected: `EXIT=0`. 각 모듈 `build/test-results/test/*.xml`에서 `failures=0 errors=0`, 변경 클래스의 `tests` 카운트 확인:
- `EventConsumerSupportTest` = 4
- `OrderEventConsumerIdempotencyTest` = 1
- `ConsumerIdempotencyIntegrationTest` = 3

- [ ] **Step 2: 라이브 스모크 필요성 판단**

이 변경은 순수 소비자측 멱등 로직이며 새 엔드포인트/부팅 경로가 없고, 동시성을 실DB(Testcontainers PostgreSQL)로 이미 증명한다. 따라서 **풀스택 라이브 스모크는 생략 가능**(스키마/마이그레이션 변경 없음, 소비자 시그니처 불변). 판단 근거를 PR 본문에 명시한다. (의심되면 docker postgres만으로 `:carry-app:test` 재실행으로 충분.)

---

### Task 7: PR 생성 + 자율 머지

**Files:** 없음

- [ ] **Step 1: develop와 동기 확인**

Run: `git fetch origin -q ; git rev-list --left-right --count HEAD...origin/develop ; echo "EXIT=$?"`
Expected: 우측(develop only)이 0이거나, 0이 아니면 충돌 없는지 확인 후 진행.

- [ ] **Step 2: 푸시 + PR(base develop)**

```bash
git push -u origin feature/consumer-idempotency-claim-first
gh pr create --base develop --title "feat(idempotency): ProcessedEvent claim-first 하드닝 — 동시 중복에서도 block-at-most-once" --body-file .pr-body-tmp.md
```
PR 본문(`.pr-body-tmp.md`)에 포함: 문제(process-then-mark + merge()로 동시 block-at-most-once 미보장) / 해결(claim-first + ON CONFLICT) / 검증(3모듈 GREEN, marker>1 뮤테이션 RED 확인) / 라이브 스모크 생략 근거 / 범위(소비자 6종·ProcessedEvent 무변경) / 후속(#90/#91 빚 해소). 한국어 본문.

- [ ] **Step 3: 임시 파일 정리 (별도 호출)**

```bash
rm .pr-body-tmp.md
```

- [ ] **Step 4: 자율 머지 (직접 머지 — repo auto-merge 비활성)**

```bash
gh pr merge <PR번호> --merge --delete-branch
```

- [ ] **Step 5: 로컬 정리**

```bash
git checkout develop && git fetch --prune && git pull --ff-only origin develop
```

---

## 완료 기준

- [ ] `EventConsumerSupport`가 claim-first(claim → block, `existsById`/`save` 제거)로 동작.
- [ ] `ProcessedEventRepository.claim()` 네이티브 `ON CONFLICT` 메서드 존재.
- [ ] 단위(`EventConsumerSupportTest` 4건) + 컨슈머 배선(`OrderEventConsumerIdempotencyTest` 1건) + 통합(`ConsumerIdempotencyIntegrationTest` 3건) 모두 GREEN.
- [ ] 동시 테스트가 `marker == 1`을 단언하고, 프로덕션 순서 원복 시 RED 됨을 확인.
- [ ] PR develop 머지 완료, 브랜치 삭제.
- [ ] 메모리 갱신: [[carry-platform-known-debts]]의 "refresh claim-first 하드닝" 항목을 ✅ 해소로 이동, [[carry-platform-roadmap-progress]] 다음 후보에서 ① 제거.
