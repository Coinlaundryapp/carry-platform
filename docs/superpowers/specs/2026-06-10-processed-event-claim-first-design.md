# ProcessedEvent claim-first 멱등 소비자 하드닝 — 설계

> 작성일: 2026-06-10 · 브랜치: `feature/consumer-idempotency-claim-first`
> 선행: #90/#91(멱등성 회귀 테스트)에서 후속으로 분리한 빚

## 1. 배경 / 문제

`EventConsumerSupport.processIfNotDuplicate`(carry-infra-kafka)는 Kafka 소비자측 멱등성의
단일 관문이다(소비자 6종이 모두 경유). 현재 제어 흐름은 **process-then-mark**다:

```
existsById(eventId)  →  block()  →  save(ProcessedEvent(eventId))
   (읽기 선체크)        (부수효과)      (처리 마킹)
```

두 가지 결함이 결합해 **동시성에서 block-at-most-once를 보장하지 못한다**:

1. **마킹이 block 뒤에 온다.** 두 스레드가 동시에 들어오면 둘 다 `existsById`를 통과한 뒤
   **둘 다 block을 실행**한다. 마킹 시점이 늦어 선점 효과가 없다.
2. **`save()`가 INSERT가 아니다.** `ProcessedEvent`는 할당식 `@Id`이고 `Persistable` 미구현이라
   Spring Data가 `merge()`(SELECT 선행)를 호출한다. 패자 스레드의 merge-SELECT가 승자 커밋
   이후 실행되면 INSERT 대신 no-op UPDATE가 되어 **PK 위반 없이** 커밋된다.

현행 통합테스트(`ConsumerIdempotencyIntegrationTest`)는 이 한계를 명시적으로 문서화하고
동시 케이스에서 `processed_events == 1`(DB dedup)만 단언하며, 부수효과는 `marker ∈ [1, threads]`로
열어 두었다.

### 현실 위협모델 (왜 라이브 버그는 아닌가)

Kafka는 같은 키(aggregateId) 이벤트를 같은 파티션 → 단일 컨슈머 스레드로 **순차** 처리하고
재배달도 순차다. 따라서 동일 이벤트의 진짜 동시 소비는 프로덕션에서 발생하지 않는다.
이 작업은 **라이브 장애 수정이 아니라 정확성 방어(defense-in-depth) + 테스트 teeth 강화**다.

## 2. 목표 / 비목표

**목표**
- 동시 중복 배달에서도 **block(부수효과)이 정확히 1회** 실행됨을 보장(block-at-most-once).
- 기존 불변식 보존: ①순차 dedup 1회 ②block 실패 시 마킹·부수효과 함께 롤백(at-least-once 재처리).
- 동시 테스트를 `marker == 1`로 강화해 회귀를 막는 teeth 확보.

**비목표**
- 소비자 6종의 시그니처/호출 코드 변경 (X — `processIfNotDuplicate` 시그니처 불변).
- Clock 주입 (X — `processedAt`은 인프라 마커 타임스탬프로 Clock 스코프 밖, 기존 결정 유지).
- retry/backoff·DLQ 정책 변경 (X — 범위 밖).

## 3. 설계

### 3.1 claim 메커니즘 — 네이티브 `ON CONFLICT DO NOTHING`

`ProcessedEventRepository`에 원자적 선점 메서드를 추가한다:

```kotlin
@Modifying
@Query(
    value = "INSERT INTO processed_events (id, processed_at) VALUES (:id, :processedAt) " +
            "ON CONFLICT (id) DO NOTHING",
    nativeQuery = true,
)
fun claim(@Param("id") id: String, @Param("processedAt") processedAt: Instant): Int
```

- 반환값 = 영향 행수. **1 = 선점 성공**, **0 = 이미 처리됨(중복)**.
- 예외를 제어 흐름으로 쓰지 않으므로 트랜잭션이 오염되지 않는다(rollback-only 회피).
  — 대안인 `Persistable + saveAndFlush + catch DataIntegrityViolationException`은 flush 예외가
  JPA tx를 rollback-only로 만들어 같은 tx 정상 return 시 `UnexpectedRollbackException` 위험이 있어
  배제했다.
- Postgres 전용 구문. 영속 계층 테스트는 Testcontainers PostgreSQL(통합) 또는 mockk(단위)라 제약 없음.

### 3.2 claim-first 재배치 — `EventConsumerSupport.processIfNotDuplicate`

```kotlin
@Transactional
fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
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
        span.error(e)
        throw e
    } finally {
        MDC.remove("saga.traceId")
        span.end()
    }
}
```

- `existsById` 선체크와 끝의 `save()`를 제거. claim 한 번이 두 역할을 원자적으로 대체.
- block은 claim 성공 시에만, **같은 트랜잭션**에서 실행된다.

### 3.3 동시성 정합성 (목표 달성 근거)

스레드 A가 claim INSERT(미커밋, `id` 유니크 인덱스 락 보유). 스레드 B가 같은 id로 claim 시
Postgres `ON CONFLICT`는 A의 tx 종료를 **대기**한 뒤:

| A의 결말 | B의 claim 결과 | B의 동작 | 결과 |
|---|---|---|---|
| 커밋 | 충돌 → 0행 | block 미실행, skip | block 1회 ✓ |
| 롤백(block 실패) | INSERT 성공 → 1행 | block 실행 | 유실 없음(재처리) ✓ |

### 3.4 에러 처리

block이 예외를 던지면 전파되고 `@Transactional`이 롤백 → claim INSERT도 롤백 → 마커 없음 →
at-least-once 재배달 시 재처리된다. 기존 동작과 동일.

## 4. 테스트 (TDD)

### 4.1 단위 — `EventConsumerSupportTest` (mockk, DB 없음)

`existsById`/`save` 기반 단언을 `claim` 기반으로 재작성:
- `claim() == 1`이면 block을 실행한다.
- `claim() == 0`이면 block을 실행하지 않는다(중복 skip).
- block이 예외를 던지면 전파된다. (롤백 불변식은 트랜잭션 관심사 → 통합으로 이전; 여기선 전파만.)
- **순서 검증**: claim()이 block보다 먼저 호출된다(mockk `verifyOrder` 또는 호출 기록).

### 4.2 통합 — `ConsumerIdempotencyIntegrationTest` (Testcontainers PostgreSQL)

- **순차 dedup** 테스트: 변경 없음 — `marker == 1`, `processed == 1` 유지.
- **실패 롤백** 테스트: 변경 없음 — `marker == 0`, `processed == 0` 유지.
- **동시 8스레드** 테스트 **강화**(이 변경의 핵심 teeth):
  - 기존: `processed == 1`, `marker ∈ [1, threads]`
  - 강화: **`marker == 1`**, `processed == 1`, **`ok == threads`**(패자도 예외 없이 깨끗이 skip).
  - 클래스/메서드 주석의 "block 1회는 동시성에서 미보장" 경고를 "claim-first로 보장됨"으로 갱신.

### 4.3 뮤테이션(teeth) 검증

강화된 동시 테스트가 다음에서 RED 되는지 확인 후 원복:
- claim 재배치를 원복(process-then-mark)하면 `marker == 1` 단언이 깨진다.
- claim을 `merge()` 기반 save로 되돌리면 동시성에서 `marker > 1`이 관측된다.

## 5. 검증 흐름

1. JDK21로 `:carry-infra-kafka:test` + `:carry-app:test`(Testcontainers) GREEN — JUnit XML로 카운트 확인.
2. 강화된 동시 테스트 단독 반복 실행(`--rerun-tasks`)으로 flaky 0 확인.
3. PR(base develop) → 자율 머지.

## 6. 영향 범위

| 파일 | 변경 |
|---|---|
| `carry-infra-kafka/.../ProcessedEventRepository.kt` | `claim()` 네이티브 메서드 추가 |
| `carry-infra-kafka/.../EventConsumerSupport.kt` | claim-first 재배치, existsById/save 제거 |
| `carry-infra-kafka/.../EventConsumerSupportTest.kt` | claim 기반 재작성 + 순서 검증 |
| `carry-app/.../ConsumerIdempotencyIntegrationTest.kt` | 동시 테스트 강화 + 주석 갱신 |
| `ProcessedEvent.kt` | 무변경 |
| 소비자 6종 | 무변경 |
