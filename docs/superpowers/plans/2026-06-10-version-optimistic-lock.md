# @Version 낙관적 락 일관 적용 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Payment·Delivery·Review 애그리거트 루트에 JPA `@Version` 낙관적 락을 Order/Dispatch와 동일하게 적용해, 동시 변경 시 lost update 대신 충돌(409 `CONCURRENT_MODIFICATION`)이 나도록 한다.

**Architecture:** 3개 JPA 엔티티 body에 `@Version var version: Long = 0`(`protected set`) 추가 + 각 테이블에 `version` 컬럼을 더하는 Flyway V20~V22(전역 시퀀스). 도메인 모델·매퍼·예외 처리는 무변경(`GlobalExceptionHandler`가 `OptimisticLockingFailureException`을 이미 409로 전역 처리). 검증은 `ConcurrencyIntegrationTest`에 결정적(스레드 비의존) 낙관락 충돌 테스트 3건.

**Tech Stack:** Kotlin, Spring Data JPA(`@Version`), Flyway, PostgreSQL, JUnit5, AssertJ, Testcontainers, `TransactionTemplate`, Gradle(JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-10-version-optimistic-lock-design.md`

---

## 사전 환경 메모 (실행자 필독)

- **JDK 21 필수.** `gradle.properties`에 `org.gradle.java.home=C:/Users/Eisen/.jdks/ms-21.0.7` 확인(미커밋, 스테이징 금지). 루트 `ROADMAP.md`(??)도 금지.
- **테스트 검증은 JUnit XML로.** `carry-app/build/test-results/test/TEST-com.carry.app.concurrency.ConcurrencyIntegrationTest.xml`의 `tests`/`failures`/`errors` + `<testcase>` 확인. Testcontainers 로그(Ryuk/postgres)도 `system-out`에 보여야 함.
- 종료코드: `; echo "EXIT=$?"`(파이프 금지). 커밋: 한국어 본문 + 영어 prefix + `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`, `git commit -F - <<'EOF' ... EOF`. Bash 도구 사용.
- ⚠️ **로컬 Docker 주의:** 무관한 `sparkplug-kafka` 컨테이너가 9092를 점유 중일 수 있음 — carry-app:test는 postgres만 쓰므로 무관하나, 만약 carry-infra-kafka 테스트를 돌릴 일 있으면 그 플레이크는 본 작업과 무관.

---

## File Structure

| 파일 | 책임 | 변경 |
|---|---|---|
| `carry-payment/.../entity/PaymentJpaEntity.kt` | Payment 영속 + 낙관락 | `@Version` 필드 추가 |
| `carry-delivery/.../entity/DeliveryJpaEntity.kt` | Delivery 영속 + 낙관락 | `@Version` 필드 추가 |
| `carry-review/.../entity/ReviewJpaEntity.kt` | Review 영속 + 낙관락 | `@Version` 필드 추가 |
| `carry-payment/.../db/migration/V20__add_version_to_payments.sql` | 스키마 | 신규 |
| `carry-delivery/.../db/migration/V21__add_version_to_deliveries.sql` | 스키마 | 신규 |
| `carry-review/.../db/migration/V22__add_version_to_reviews.sql` | 스키마 | 신규 |
| `carry-app/.../concurrency/ConcurrencyIntegrationTest.kt` | 동시성 검증 | 낙관락 테스트 3건 + 주입/시드 |
| `carry-app/.../test/TestFixtures.kt` | 테스트 격리 | `truncateAll`에 review 정리 추가 |

**확정된 사실(탐색 완료):**
- 미러링 대상 패턴 = `OrderJpaEntity` 본문 `@Version @Column(nullable=false) var version: Long = 0` + `protected set`.
- 전역 Flyway 최대 = **V19**(다음 V20/V21/V22 가용).
- 생성자: `PaymentJpaEntity(invoiceId, orderId, customerId, status: PaymentStatus, pgProvider: PgProvider, pgTransactionId: String?, amount: Long, paidAt: Instant?, failReason: String?)` / `DeliveryJpaEntity(orderId, dispatchId, carrierId, laundromatId, status: DeliveryStatus, actualWeight: BigDecimal?, steps 기본값)` / `ReviewJpaEntity(laundromatId, customerId, comment: String?, rating: Int, mediaList 기본값)`.
- enum: `PaymentStatus.PENDING`, `PgProvider.TOSS_PAYMENTS`, `DeliveryStatus.PICKUP_PENDING`. invoice status 문자열 = `'ISSUED'`.
- FK: `payment_payments.invoice_id → payment_invoices(id)`(선 seed 필요). delivery/review 모듈 간 참조는 FK 없음.
- `payment_invoices(order_id)` UNIQUE → seed 시 고유 order_id 사용.
- repo: `PaymentJpaRepository`/`DeliveryJpaRepository`/`ReviewJpaRepository` (각 `JpaRepository<…, Long>`).
- `TestFixtures.truncateAll`에 `review_reviews`/`review_media` DELETE 없음(추가 필요). `review_media`는 `ON DELETE CASCADE`.

---

## Chunk 1: 낙관락 테스트 작성 (RED)

> @Version이 아직 없으므로 stale 저장이 예외 없이 성공(last-write-wins) → 3건 모두 RED. 이 RED가 곧 teeth 증명(락 없으면 통과 못 함).

### Task 1: TestFixtures 격리 보강 + ConcurrencyIntegrationTest 낙관락 테스트 3건

**Files:**
- Modify: `carry-app/src/test/kotlin/com/carry/app/test/TestFixtures.kt`
- Modify: `carry-app/src/test/kotlin/com/carry/app/concurrency/ConcurrencyIntegrationTest.kt`

- [ ] **Step 1: `TestFixtures.truncateAll`에 review 정리 추가**

`truncateAll`의 SQL 블록에서 `DELETE FROM payment_payments;` 줄 위(또는 dispatch/delivery 그룹 근처, 자식-부모 순서 유지) 적당한 위치에 두 줄 추가 — `review_media`(자식) → `review_reviews`(부모):
```sql
            DELETE FROM review_media;
            DELETE FROM review_reviews;
```
(다른 테이블 삭제 순서는 그대로. review 테이블은 다른 테이블과 FK가 없어 위치는 자유롭지만 자식→부모 순서만 지킨다.)

- [ ] **Step 2: ConcurrencyIntegrationTest에 import·주입·시드·테스트 3건 추가**

import 추가(ktlint ASCII 정렬 준수). ⚠️ `org.springframework.dao.OptimisticLockingFailureException`은 **이미 import됨(line 26) — 재추가 금지**:
```kotlin
import com.carry.delivery.adapter.outbound.persistence.entity.DeliveryJpaEntity
import com.carry.delivery.adapter.outbound.persistence.repository.DeliveryJpaRepository
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.PaymentJpaRepository
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import com.carry.review.adapter.outbound.persistence.entity.ReviewJpaEntity
import com.carry.review.adapter.outbound.persistence.repository.ReviewJpaRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
```

클래스 본문(기존 `@Autowired` 블록 아래)에 주입 추가. ⚠️ **`TransactionTemplate`은 Spring Boot 자동 등록 빈이 아니므로 `@Autowired` 불가** — `PlatformTransactionManager`를 주입받아 직접 생성한다(공유 `SagaIntegrationTestConfig` 무수정):
```kotlin
    @Autowired lateinit var paymentJpaRepository: PaymentJpaRepository
    @Autowired lateinit var deliveryJpaRepository: DeliveryJpaRepository
    @Autowired lateinit var reviewJpaRepository: ReviewJpaRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    private val tx by lazy { TransactionTemplate(transactionManager) }
```

낙관락 충돌 테스트 3건 + 시드 헬퍼를 클래스 본문에 추가:
```kotlin
    // --- @Version 낙관적 락 일관 적용 (Payment·Delivery·Review) ---

    private fun seedInvoiceId(orderId: Long): Long =
        jdbc.queryForObject(
            "INSERT INTO payment_invoices(order_id, customer_id, status, weight, total_amount) " +
                "VALUES (?, 1, 'ISSUED', 1.00, 1000) RETURNING id",
            Long::class.java, orderId,
        )!!

    @Test
    fun `같은 Payment를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val invoiceId = seedInvoiceId(orderId = 90001L)
        val id = tx.execute {
            paymentJpaRepository.save(
                PaymentJpaEntity(
                    invoiceId = invoiceId, orderId = 90001L, customerId = 1L,
                    status = PaymentStatus.PENDING, pgProvider = PgProvider.TOSS_PAYMENTS,
                    pgTransactionId = null, amount = 1000L, paidAt = null, failReason = null,
                ),
            ).id
        }!!
        val stale = tx.execute { paymentJpaRepository.findById(id).get() }!!          // detached, v0
        // step3: fresh 로드 후 스칼라 변경 → dirty-checking이 커밋 시 flush, version 0→1
        tx.execute { paymentJpaRepository.findById(id).get().apply { failReason = "first" } }
        // step4: detached stale(v0) 변경 후 save → merge가 version 불일치 감지
        stale.failReason = "second"
        assertThatThrownBy { tx.execute { paymentJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }

    @Test
    fun `같은 Delivery를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val id = tx.execute {
            deliveryJpaRepository.save(
                DeliveryJpaEntity(
                    orderId = 90002L, dispatchId = 1L, carrierId = 1L, laundromatId = 1L,
                    status = DeliveryStatus.PICKUP_PENDING, actualWeight = null,
                ),
            ).id
        }!!
        val stale = tx.execute { deliveryJpaRepository.findById(id).get() }!!
        tx.execute { deliveryJpaRepository.findById(id).get().apply { actualWeight = BigDecimal("1.00") } }
        stale.actualWeight = BigDecimal("2.00")   // 스칼라만 변경 — steps 컬렉션은 절대 건드리지 않음
        assertThatThrownBy { tx.execute { deliveryJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }

    @Test
    fun `같은 Review를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val id = tx.execute {
            reviewJpaRepository.save(
                ReviewJpaEntity(laundromatId = 1L, customerId = 1L, comment = "c", rating = 5),
            ).id
        }!!
        val stale = tx.execute { reviewJpaRepository.findById(id).get() }!!
        tx.execute { reviewJpaRepository.findById(id).get().apply { comment = "first" } }
        stale.comment = "second"
        assertThatThrownBy { tx.execute { reviewJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }
```

> 메커니즘: `BaseEntity.id`는 save 후 채워짐(`save(...).id` non-null). `tx.execute {}`로 각 단계를 독립 트랜잭션에 두어 `stale`이 detached 되게 한다. step3는 dirty-checking 커밋 flush(명시 save 불필요), step4는 detached 엔티티 save=merge라 Hibernate가 stale version 대 DB version 불일치를 감지해 `ObjectOptimisticLockingFailureException`(=`OptimisticLockingFailureException` 하위)을 던진다. `version`은 `protected set`이라 테스트가 직접 못 바꾸며, 스칼라 변경으로 UPDATE를 유발한다. (step3 dirty-check flush는 Hibernate 2차 캐시 off 전제 — 현 test 컨텍스트는 L2C 미설정이라 OK. 만약 @Version 추가 후에도 RED면 step3가 flush 안 된 것이니 `saveAndFlush`로 강제.)

- [ ] **Step 3: RED 확인**

Run: `./gradlew :carry-app:test --tests "com.carry.app.concurrency.ConcurrencyIntegrationTest" ; echo "EXIT=$?"`
Expected: **실패**(`EXIT` ≠ 0). 아직 `@Version`이 없어 stale save가 예외 없이 성공 → 3개 신규 테스트의 `assertThatThrownBy`가 "no exception thrown"으로 실패(RED). 기존 테스트(배차/주문)는 통과. **커밋하지 않음.**

> RED가 안 나면(이미 통과) 중단하고 원인 파악 — @Version 없이 통과하면 테스트가 가짜다.

---

## Chunk 2: @Version + 마이그레이션 (GREEN)

### Task 2: 3개 엔티티에 @Version + V20~V22 마이그레이션

**Files:**
- Modify: `carry-payment/.../entity/PaymentJpaEntity.kt`, `carry-delivery/.../entity/DeliveryJpaEntity.kt`, `carry-review/.../entity/ReviewJpaEntity.kt`
- Create: `V20__add_version_to_payments.sql`, `V21__add_version_to_deliveries.sql`, `V22__add_version_to_reviews.sql`

- [ ] **Step 1: 각 엔티티에 `@Version` 필드 추가**

세 엔티티 모두 `import jakarta.persistence.Version` 추가(`Column`은 이미 import됨). 클래스 body 첫 부분(`) : BaseEntity() {` 직후, `fun toDomain()` 위)에 추가:
```kotlin

    /**
     * JPA optimistic locking 카운터. 두 트랜잭션이 동일 애그리거트를 동시 변경하면
     * 두 번째 commit에서 OptimisticLockingFailureException이 발생한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set
```

- [ ] **Step 2: 마이그레이션 3개 생성**

`carry-payment/src/main/resources/db/migration/V20__add_version_to_payments.sql`:
```sql
-- 낙관적 락: 동시 변경 시 두 번째 커밋의 WHERE id=? AND version=? 매칭 실패 → OptimisticLockingFailureException.
ALTER TABLE payment_payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```
`carry-delivery/src/main/resources/db/migration/V21__add_version_to_deliveries.sql`:
```sql
-- 낙관적 락: 동시 변경 시 두 번째 커밋의 WHERE id=? AND version=? 매칭 실패 → OptimisticLockingFailureException.
ALTER TABLE delivery_deliveries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```
`carry-review/src/main/resources/db/migration/V22__add_version_to_reviews.sql`:
```sql
-- 낙관적 락: 동시 변경 시 두 번째 커밋의 WHERE id=? AND version=? 매칭 실패 → OptimisticLockingFailureException.
ALTER TABLE review_reviews ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

- [ ] **Step 3: GREEN 확인**

Run: `./gradlew :carry-app:test --tests "com.carry.app.concurrency.ConcurrencyIntegrationTest" ; echo "EXIT=$?"`
Expected: `EXIT=0`. XML에 신규 3건 포함 전체 통과(`failures="0" errors="0"`). Flyway가 V20~V22 적용 후 ddl-auto:validate 통과(엔티티 version ↔ 컬럼 일치).

> validate 실패가 나면(엔티티엔 version 있는데 컬럼 없음 등) 마이그레이션 누락/오타 점검.

- [ ] **Step 4: 영향 모듈 컴파일 회귀 확인**

Run: `./gradlew :carry-payment:compileKotlin :carry-delivery:compileKotlin :carry-review:compileKotlin ; echo "EXIT=$?"`
Expected: `EXIT=0`.

---

## Chunk 3: 커밋 + 검증 + PR

### Task 3: 커밋 (production · 테스트 분리)

- [ ] **Step 1: production 커밋(@Version + 마이그레이션)**

```bash
git add carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/entity/PaymentJpaEntity.kt \
        carry-delivery/src/main/kotlin/com/carry/delivery/adapter/outbound/persistence/entity/DeliveryJpaEntity.kt \
        carry-review/src/main/kotlin/com/carry/review/adapter/outbound/persistence/entity/ReviewJpaEntity.kt \
        carry-payment/src/main/resources/db/migration/V20__add_version_to_payments.sql \
        carry-delivery/src/main/resources/db/migration/V21__add_version_to_deliveries.sql \
        carry-review/src/main/resources/db/migration/V22__add_version_to_reviews.sql
git commit -F - <<'EOF'
feat(persistence): Payment·Delivery·Review에 @Version 낙관적 락 적용

Order/Dispatch에만 있던 @Version을 나머지 애그리거트 루트 3개에 일관 적용.
엔티티 @Version 필드(protected set) + Flyway V20~V22로 version 컬럼 추가.
동시 변경 시 OptimisticLockingFailureException → GlobalExceptionHandler가
409 CONCURRENT_MODIFICATION 처리(기존 전역, 무변경).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

- [ ] **Step 2: 테스트 커밋(낙관락 검증 + 격리 보강)**

```bash
git add carry-app/src/test/kotlin/com/carry/app/concurrency/ConcurrencyIntegrationTest.kt \
        carry-app/src/test/kotlin/com/carry/app/test/TestFixtures.kt
git commit -F - <<'EOF'
test(persistence): Payment·Delivery·Review 낙관락 충돌 결정적 검증 3건

ConcurrencyIntegrationTest에 detached stale save→OptimisticLockingFailureException
테스트 추가(TransactionTemplate로 독립 tx, 스칼라 변경, steps 컬렉션 비건드림).
TestFixtures.truncateAll에 review_media/review_reviews 정리 보강.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 4: 전체 검증

**Files:** 없음

- [ ] **Step 1: carry-app 전체 테스트**

Run: `./gradlew :carry-app:test ; echo "EXIT=$?"`
Expected: `EXIT=0`. `ConcurrencyIntegrationTest`에 신규 3건 포함 전체 GREEN. Flyway V20~V22 적용 + validate 통과.

- [ ] **Step 2: 라이브 스모크 불요 판단**

스키마 추가 컬럼 + 영속 계층 락. 동시성을 실 PostgreSQL(Testcontainers)로 증명, 새 부팅/엔드포인트 없음. 라이브 풀스택 스모크 생략 — PR 본문에 근거 명시.

---

### Task 5: PR + 자율 머지

**Files:** 없음

- [ ] **Step 1: develop 동기 확인**

Run: `git fetch origin -q ; git rev-list --left-right --count HEAD...origin/develop ; echo "EXIT=$?"`
Expected: 우측(develop only)=0 또는 충돌 없으면 진행.

- [ ] **Step 2: 푸시 + PR(base develop)**

```bash
git push -u origin feature/version-optimistic-lock
gh pr create --base develop --title "feat(persistence): @Version 낙관적 락 일관 적용 — Payment·Delivery·Review" --body-file .pr-body-tmp.md
```
PR 본문(`.pr-body-tmp.md`, 한국어): 문제(Order/Dispatch만 @Version, 나머지 lost update) / 해결(@Version + V20~V22 + 기존 전역 예외처리) / 검증(결정적 낙관락 3건, RED→GREEN) / 범위(루트 3개, 도메인·매퍼·예외 무변경) / 라이브 스모크 생략 근거.

- [ ] **Step 3: 임시 파일 정리(별도 호출)**

```bash
rm .pr-body-tmp.md
```

- [ ] **Step 4: 자율 머지(직접 머지 — repo auto-merge 비활성)**

```bash
gh pr merge <PR번호> --merge --delete-branch
```

- [ ] **Step 5: 로컬 정리**

```bash
git checkout develop && git fetch --prune && git pull --ff-only origin develop
```

---

## 완료 기준

- [ ] Payment·Delivery·Review JPA 엔티티에 `@Version var version`(protected set) 존재.
- [ ] Flyway V20~V22가 각 테이블에 `version BIGINT NOT NULL DEFAULT 0` 추가, validate 통과.
- [ ] `ConcurrencyIntegrationTest` 낙관락 충돌 3건 GREEN(@Version 없을 때 RED였음 = teeth).
- [ ] `TestFixtures.truncateAll`이 review 테이블 정리.
- [ ] `:carry-app:test` 전체 GREEN.
- [ ] PR develop 머지 완료, 브랜치 삭제.
- [ ] 메모리 갱신: [[carry-platform-known-debts]]의 "@Version 일관 적용"을 ✅ 해소로 이동, [[carry-platform-roadmap-progress]] 반영.
