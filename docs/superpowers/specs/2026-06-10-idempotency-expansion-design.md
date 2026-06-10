# 명령측 멱등성 확장 (Payment·Review) + 메커니즘 공유 추출 설계

- 작성일: 2026-06-10
- 대상 빚: [[carry-platform-known-debts]] "명령측 멱등성 타 명령 확장" (현재 OrderCommandService만)
- 브랜치: `feature/idempotency-expansion` (base: develop)
- 선행: #101(명령측 멱등성 Idempotency-Key 도입)

## 1. 배경

#101이 `OrderCommandService.createOrder`에 클라이언트 공급 `Idempotency-Key` 기반 멱등성을 도입했다(reserve→처리→complete 3단계 + Redis/InMemory fallback). 중복 제출이 금전·통계에 해로운 다른 "생성" 명령은 아직 무방비다.

전수 조사 결과 최우선(P0, 중복 위험 ★★★) "생성" 명령은:
- **`Payment.requestPayment`** (`carry-payment/.../PaymentCommandService.kt:39`) — 중복 제출 시 **이중 청구**(PG 재호출). `@Transactional`, 외부 PG 호출 포함.
- **`Review.createReview`** (`carry-review/.../ReviewCommandService.kt`) — 중복 시 평점·통계 오염.

둘 다 Order와 동일한 "생성 후 id 반환, 재요청 시 기존 결과 재생" 형태라 #101 패턴을 그대로 재사용한다.

### 메커니즘 중복 문제 (rule of three)

#101의 멱등 저장 메커니즘은 `carry-order` 내부에 있다:
- `IdempotencyPort`(application 아웃바운드 포트, `reserve`/`findCompletedOrderId`/`complete`)
- `RedisIdempotencyAdapter`(SETNX+TTL) / `InMemoryIdempotencyStore`(fallback) / `IdempotencyStoreConfig`(nullable `StringRedisTemplate`→InMemory)

Payment·Review에 적용하면 동일 메커니즘 사용처가 **3개**가 된다 → rule of three. 모듈마다 Redis/InMemory 어댑터를 복제하면 ~3벌 중복이 생기므로, **메커니즘을 `carry-infra-redis`로 추출**해 공유한다.

## 2. 목표 / 비목표

### 목표
- `Payment.requestPayment`·`Review.createReview`에 `Idempotency-Key` 멱등성 적용(중복 제출 차단).
- 멱등 저장 메커니즘(Redis/InMemory)을 `carry-infra-redis`로 추출해 order·payment·review가 공유(중복 제거).
- 기존 Order 멱등성 **행동 보존**(포트·서비스·서비스테스트 무변경).

### 비목표 (별도 후속)
- **Dispatch/Delivery 상태전이 명령**(`claim`/`accept`/`reject`/`assign`/`pickup`/`washing`/`drying`/`delivery`) — 이들은 "생성"이 아니라 기존 애그리거트 상태전이라 replay 의미가 다르고(현재 상태 반환 + 이벤트 재발행 억제 필요), 이미 `@Version` 낙관락 + 상태가드(`transitTo`가 잘못된 전이에 예외)로 이중적용이 대체로 차단된다. 별도 설계 필요.
- `Review.updateReview`(수정은 마지막 값이 최종이라 위험 낮음), `auth/signup`(token 1회용), `shipping-address` 생성(카운트 제약).
- 응답 본문 캐싱(현재 id만 저장 후 재조회하는 #101 방식 유지).
- 사용자별 키 스코핑(전역 키 유지 — 클라이언트 공급 키가 충분히 고유).

## 3. 상세 설계

### 3.1 공유 메커니즘 — carry-infra-redis (신규)

```kotlin
// carry-infra-redis/.../redis/IdempotencyStore.kt
interface IdempotencyStore {
    /** 키를 PENDING으로 선점. 처음 1회만 true(SETNX/원자적 add). */
    fun reserve(key: String): Boolean
    /** 완료된 키의 결과 id. PENDING·부재면 null. */
    fun findCompletedId(key: String): Long?
    /** 처리 결과 id를 긴 TTL로 저장(이후 동일 키는 재생). */
    fun complete(key: String, id: Long)
}
```

```kotlin
// RedisIdempotencyStore.kt — #101 RedisIdempotencyAdapter를 일반화(keyPrefix 파라미터화)
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String,        // 예: "idem:payment:request:"
    private val pendingTtl: Duration,
    private val resultTtl: Duration,
) : IdempotencyStore {
    override fun reserve(key: String) =
        redis.opsForValue().setIfAbsent(fullKey(key), PENDING, pendingTtl) == true
    override fun findCompletedId(key: String) =
        redis.opsForValue().get(fullKey(key))?.toLongOrNull()
    override fun complete(key: String, id: Long) {
        redis.opsForValue().set(fullKey(key), id.toString(), resultTtl)
    }
    private fun fullKey(key: String) = "$keyPrefix$key"
    companion object { private const val PENDING = "PENDING" }
}
```

```kotlin
// InMemoryIdempotencyStore.kt — #101 동일 로직(프로세스-local fallback). prefix 불요(인스턴스가 곧 스코프).
class InMemoryIdempotencyStore : IdempotencyStore {
    private val completed = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    override fun reserve(key: String): Boolean {
        if (completed.containsKey(key)) return false
        return pending.add(key)
    }
    override fun findCompletedId(key: String) = completed[key]
    override fun complete(key: String, id: Long) { completed[key] = id; pending.remove(key) }
}
```

- 추출 원칙: **순수 메커니즘**, 모듈/도메인 무관. 키 프리픽스로 모듈 간 키공간 분리.
- `StringRedisTemplate`은 Spring Boot `RedisAutoConfiguration` 자동 빈. carry-infra-redis는 이미 모든 모듈의 redis 인프라 위치이며 `spring-boot-starter-data-redis`를 `api`로 노출하므로, carry-infra-redis 의존만 추가하면 `StringRedisTemplate`이 전이된다.
- **와이어링 사실**: `RedisAutoConfiguration` 제외는 `carry-app/src/test/resources/application-test.yml`에만 있다. carry-order/payment/review의 자체 테스트는 `@SpringBootTest`가 아니라 순수 mockk 단위 테스트라 nullable→InMemory 분기는 **carry-app 통합 컨텍스트에서만** 실행된다. 모듈별 추가 test 프로파일 설정은 불요.
- ⚠️ **carry-infra-redis는 현재 `src/test` 디렉터리·test 의존성이 전무**(build.gradle.kts에 `api(starter-data-redis)`만). 공유 store 테스트를 옮기려면 `testImplementation`으로 junit-jupiter·assertj·mockk를 추가해야 한다(아래 §4).

### 3.2 각 모듈 — 헥사고날 포트 유지 + 공유 store 위임

각 application 모듈은 **자체 아웃바운드 포트**(기존 order 스타일)를 두고, thin adapter가 공유 store에 모듈 prefix로 위임한다. application→infra 직결을 피해 헥사고날 일관성 유지.

**carry-order (경량 마이그레이션, 행동 보존)**:
- `IdempotencyPort`(기존, `reserve`/`findCompletedOrderId`/`complete`) **무변경** → `OrderCommandService`·서비스테스트 무변경.
- 신규 `OrderIdempotencyAdapter(store: IdempotencyStore) : IdempotencyPort` — `findCompletedOrderId`→`store.findCompletedId` 등 3줄 위임.
- `IdempotencyStoreConfig`: 공유 store(Redis or InMemory, prefix `idem:order:create:`) 생성 후 `OrderIdempotencyAdapter`로 래핑.
- **삭제**: 기존 `RedisIdempotencyAdapter`·`InMemoryIdempotencyStore`(order). 어댑터 단위테스트(`RedisIdempotencyAdapterTest`·`InMemoryIdempotencyStoreTest`)는 carry-infra-redis로 이전(공유 클래스 검증).

**carry-payment (신규 적용)**:
- 신규 포트 `PaymentIdempotencyPort`(`reserve`/`findCompletedPaymentId`/`complete`) + `PaymentIdempotencyAdapter`(공유 store 위임, prefix `idem:payment:request:`) + `@Configuration`(nullable `StringRedisTemplate` fallback).
- `RequestPaymentCommand`에 `idempotencyKey: String? = null` 추가.
- `PaymentController`(POST `/api/v2/payments/pay`)에 `@RequestHeader(value="Idempotency-Key", required=false)` → command 주입.
- `PaymentCommandService.requestPayment` 3단계 삽입:
  - 맨 앞: `key?.let { idempotencyPort.findCompletedPaymentId(it)?.let { id -> return findPayment(id) }; if (!reserve(it)) throw 409 }`
  - **reserve는 PG 호출·저장 이전**(이중 청구 방지 핵심).
  - 성공/실패 양 분기의 `save` 직후 `key?.let { idempotencyPort.complete(it, saved.id!!) }`. 실패(FAILED) 결과도 complete — 동일 키 재시도는 그 결과를 재생(정상 멱등 의미). 진짜 재시도는 새 키 사용.
  - PG 예외(CB OPEN 등) 전파 시 DB 트랜잭션은 롤백되지만 `reserve`로 쓴 Redis PENDING 마커는 **트랜잭션 밖**이라 롤백되지 않는다. 따라서 complete 미호출 + **pendingTtl(120s) 만료로만** 해소 → 그 윈도우 동안 같은 키 재시도는 409(진짜 재시도는 새 키). #101 order 동작과 동일(회귀 아님). 향후 catch에서 `release(key)`로 즉시 해소하는 개선은 범위 밖.
  - replay용 `findPayment(id)` = `paymentPersistencePort.findById(id): Payment?`(이미 존재, 보강 불요).
- carry-payment build.gradle.kts에 `implementation(project(":carry-infra-redis"))` 추가.

**carry-review (신규 적용)**:
- 동일 패턴. 포트 `ReviewIdempotencyPort`(`findCompletedReviewId`), prefix `idem:review:create:`.
- `CreateReviewCommand`에 `idempotencyKey` 추가, `ReviewController` POST `/api/v2/reviews` 헤더, `ReviewCommandService.createReview` 3단계, replay `findReview(id)` = `reviewPersistencePort.findById(id): Review?`(이미 존재).
- build.gradle.kts에 carry-infra-redis 의존 추가.

> **서비스 생성자 변경(테스트 영향)**: `PaymentCommandService`는 8번째 인자로 `PaymentIdempotencyPort`, `ReviewCommandService`는 신규 `ReviewIdempotencyPort` 인자를 받는다. 기존 `PaymentCommandServiceTest`·`ReviewCommandServiceTest`는 SUT를 직접 생성하므로 `mockk<...IdempotencyPort>(relaxed=true)`를 추가해야 한다(키 미제공 기존 테스트는 멱등 경로 미진입). OrderCommandServiceTest의 Idempotency `@Nested`(완료키 재생 / reserve 실패→409 / 신규키→complete) 3케이스가 정확한 템플릿.

### 3.3 충돌/에러
- 진행 중 키: `BusinessException(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS, ...)` (기존 ErrorCode·전역 핸들러 409 재사용, 신규 코드 불요).
- 키 미제공(`null`): 멱등성 미적용(기존 동작 그대로) — 하위호환.

## 4. 테스트 계획 (TDD)

- **공유 store 단위**(carry-infra-redis): `InMemoryIdempotencyStore`(reserve 1회·complete 후 재생·complete 후 reserve 불가) + `RedisIdempotencyStore`(StringRedisTemplate mock으로 SETNX/get/set·**prefix 결합 검증** — 일반화 후 테스트가 keyPrefix를 명시 전달해 `"$keyPrefix$key"` 커버리지 유지). #101 order 어댑터 테스트 이전·일반화.
  - ⚠️ **carry-infra-redis/build.gradle.kts에 test 의존성 추가 필수**: `testImplementation`으로 junit-jupiter(platform)·`org.assertj:assertj-core`·`io.mockk:mockk`(버전은 타 모듈 build.gradle.kts 관용구 따름). `src/test/kotlin` 신규 생성.
- **PaymentCommandService 멱등 3케이스**(OrderCommandServiceTest 미러): 완료 키 재생(PG·save 0회), 진행 중(reserve 실패)→409, 신규 키→reserve 후 처리·complete. mockk 기반.
- **ReviewCommandService 멱등 3케이스**: 동일.
- **carry-order 회귀**: 기존 `OrderCommandServiceTest` 멱등 3건 GREEN 유지(무변경 확인).
- **carry-app 통합**(선택, 가능하면): payment/review 동시 중복 제출 1건씩(기존 `ConsumerIdempotencyIntegrationTest` 동시성 scaffolding 재사용) — InMemory store로 동작.

## 5. 검증
- 영향 모듈 `:carry-infra-redis:test :carry-order:test :carry-payment:test :carry-review:test :carry-app:test`.
- JUnit XML tests/failures 수치로 확인(BUILD SUCCESSFUL 불신, [[verify-gradle-tests-via-junit-xml]]).
- JDK21(`org.gradle.java.home` 미커밋 유지).

## 6. 리스크 / 완화
| 리스크 | 완화 |
|---|---|
| Order 마이그레이션이 동작 깨뜨림 | 포트·서비스 시그니처 무변경, thin adapter만 교체. 기존 멱등 3 + 어댑터 테스트가 안전망. 마지막 chunk라 문제 시 분리 가능(payment/review는 order 무의존). |
| Payment reserve가 PG 호출 뒤로 가면 이중청구 | 명세상 reserve를 PG·저장 **이전**에 배치(테스트로 호출순서 검증). |
| 실패 결과 complete로 재시도 막힘 | 멱등 키 의미상 정상(같은 키=같은 작업). 진짜 재시도는 새 키. 문서 명시. |
| 모듈→carry-infra-redis 의존 추가 | 이미 공유 인프라 모듈. order는 기존 의존. payment/review만 추가. |
| 크로스모듈 Spring 빈 와이어링 | 각 모듈이 자체 `@Configuration`으로 store+adapter 빈 등록(컴포넌트 스캔 기존 방식). 공유 클래스는 빈 아님(직접 new). |

## 7. 범위 밖 후속(메모)
Dispatch/Delivery 상태전이 멱등성(별도 replay 의미 설계), 응답 본문 캐싱, signup/updateReview/shipping-address.
