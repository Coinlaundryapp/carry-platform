# @Version 낙관적 락 일관 적용 (Payment·Delivery·Review) — 설계

> 작성일: 2026-06-10 · known-debt(Phase 2.5 후속, "@Version을 Payment·Delivery·Review에도 일관 적용")
> 선행: Order(#17 V17)·Dispatch(#18 V18)에 @Version 적용 완료, `GlobalExceptionHandler`가 충돌을 409로 처리

## 1. 배경 / 문제

JPA `@Version` 낙관적 락은 현재 **`OrderJpaEntity`·`DispatchJpaEntity`에만** 적용돼 있다.
두 트랜잭션이 같은 애그리거트를 동시 변경하면 두 번째 commit이 `OptimisticLockingFailureException`을
던지고, `GlobalExceptionHandler`가 이를 409 `CONCURRENT_MODIFICATION`으로 변환한다.

다른 애그리거트 루트(**Payment·Delivery·Review**)에는 이 가드가 없어, 동시 변경 시 마지막 쓰기가
조용히 이긴다(lost update). known-debt에 "일관 적용" 항목으로 남아 있다.

> 현실 위협모델상 이들 애그리거트는 saga/CRUD 구동이며 같은 키 이벤트는 같은 파티션→단일 스레드로
> 순차 처리되어 실제 동시 변경 빈도는 낮다. 따라서 이 작업은 **일관성 하드닝(defense-in-depth)**이며,
> 라이브 lost-update 장애 수정이 아니다.

## 2. 목표 / 비목표

**목표**
- Payment·Delivery·Review 애그리거트 루트에 `@Version`을 Order/Dispatch와 **동일한 형태**로 적용.
- 각 테이블에 `version` 컬럼을 추가하는 Flyway 마이그레이션(ddl-auto:validate 통과).
- 각 애그리거트에서 낙관적 락 충돌이 실제로 감지됨을 결정적 테스트로 증명.

**비목표**
- 자식 엔티티(InvoiceLineItem·DeliveryStep·ReviewMedia)·`InvoiceJpaEntity`에 적용 (X — known-debt 명시 3개 루트만, YAGNI).
- 도메인 모델/`reconstitute`/`fromDomain`/`toDomain` 변경 (X — version은 Hibernate 관리, 도메인 비노출).
- 예외 처리·에러 코드 변경 (X — `GlobalExceptionHandler`가 이미 전역 처리).
- pessimistic lock·재시도 정책 (X — 범위 밖).

## 3. 설계

### 3.1 `@Version` 필드 — Order/Dispatch와 동일

세 JPA 엔티티 클래스 **body**(생성자 `) : BaseEntity() {` 이후)에 추가:

```kotlin
/**
 * JPA optimistic locking 카운터. 두 트랜잭션이 동일 <애그리거트>를 동시 변경하면
 * 두 번째 commit에서 OptimisticLockingFailureException이 발생한다.
 */
@Version
@Column(nullable = false)
var version: Long = 0
    protected set
```

- `OrderJpaEntity`(line 79~84)의 형태를 그대로 미러링. `protected set`으로 외부 변경 차단(Hibernate만 관리).
- import: `jakarta.persistence.Version` 추가(각 파일에 `@Column`은 이미 import됨).
- `fromDomain`/`toDomain`은 version을 참조하지 않으므로 무변경(Order 동일 — version은 영속 계층 전용).

대상: `PaymentJpaEntity`(`payment_payments`), `DeliveryJpaEntity`(`delivery_deliveries`), `ReviewJpaEntity`(`review_reviews`).

### 3.2 Flyway 마이그레이션 — 전역 시퀀스 V20~V22

Flyway 버전은 **모듈 전역 단일 히스토리**다(현재 전역 최대 = V19). 각 마이그레이션은 해당 모듈의
`src/main/resources/db/migration/`에 둔다(V17=carry-order, V18=carry-dispatch 패턴 동일). 세 ALTER는
서로 독립이라 V20~V22 번호 배정 순서는 무관.

```sql
-- carry-payment/src/main/resources/db/migration/V20__add_version_to_payments.sql
ALTER TABLE payment_payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- carry-delivery/src/main/resources/db/migration/V21__add_version_to_deliveries.sql
ALTER TABLE delivery_deliveries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- carry-review/src/main/resources/db/migration/V22__add_version_to_reviews.sql
ALTER TABLE review_reviews ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

`DEFAULT 0`으로 기존 행도 안전하게 채워지고, 엔티티 기본값 `0`과 일치.

### 3.3 예외 처리 — 변경 없음

`GlobalExceptionHandler.handleOptimisticLocking`이 `OptimisticLockingFailureException` →
409 `CONCURRENT_MODIFICATION`을 이미 전역 처리. 신규 엔티티도 자동 적용.

## 4. 테스트 — 접근법 B(결정적 낙관락 충돌)

`carry-app/src/test/kotlin/com/carry/app/concurrency/ConcurrencyIntegrationTest.kt`(기존 거처,
Testcontainers PostgreSQL)에 애그리거트별 1건씩, 총 3건 추가.

**왜 B(메커니즘 증명)인가 — A(실 도메인 명령 동시 실행) 대신.** 기존 Order/Dispatch 테스트는
"이중 claim/cancel" 같은 자연스러운 동시 변경 도메인 명령이 있어 A를 썼다. Payment/Delivery/Review는
saga/CRUD 구동이라 그런 명령이 없고, 이 작업은 일관성 하드닝이므로 도메인 race를 인위로 만들기보다
**`@Version` 락 메커니즘 자체가 각 엔티티에서 동작함**을 증명하는 게 적합하다.

### 4.1 필요한 테스트 인프라 (기존 클래스에 추가)
현재 `ConcurrencyIntegrationTest`는 서비스/포트/`JdbcTemplate`만 주입한다. 다음을 `@Autowired`로 추가:
- `PaymentJpaRepository`, `DeliveryJpaRepository`, `ReviewJpaRepository` (각 모듈 `@Repository`, carry-app 컨텍스트가 스캔 — 직접 주입 가능)
- `org.springframework.transaction.support.TransactionTemplate` (Spring Boot 자동 등록 빈)

### 4.2 결정적 방식 (스레드/타이밍 비의존)
1. 애그리거트 1건 저장(version=0), id 확보.
2. `TransactionTemplate`(별도 tx)로 사본 `stale` 로드 → tx 종료로 **detached**(version=0).
3. 다른 tx에서 같은 id 로드·**스칼라 필드** 수정·저장 → DB version 0→1 커밋.
4. detached `stale`의 **스칼라 필드** 수정 후 저장 → `save()`가 `merge()`를 호출, Hibernate가 stale
   version(0) vs DB version(1) 불일치를 감지 → **`OptimisticLockingFailureException`**.

```kotlin
@Test
fun `같은 Payment를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
    val invoiceId = seedInvoice()                       // FK: payment_payments.invoice_id → payment_invoices(id)
    val id = tx.execute { paymentJpaRepository.save(newPaymentEntity(invoiceId)).id!! }!!
    val stale = tx.execute { paymentJpaRepository.findById(id).get() }!!          // detached, v0
    tx.execute { paymentJpaRepository.findById(id).get().apply { failReason = "first" } } // v0→v1 (flush on commit)
    stale.failReason = "second"
    assertThatThrownBy { tx.execute { paymentJpaRepository.save(stale) } }
        .isInstanceOf(OptimisticLockingFailureException::class.java)
}
```

**엔티티별 변경 스칼라(자식 컬렉션 금지)** — `version`은 `protected set`이라 직접 못 바꾼다. UPDATE를
유발할 **공개 가변 스칼라**를 쓴다:
- Payment → `failReason`(String?) 또는 `status`
- Delivery → `actualWeight`(BigDecimal?) 또는 `status`. ⚠️ `steps` **`@OneToMany(orphanRemoval=true)` 컬렉션은 건드리지 말 것**(빈 리스트 merge 시 자식 삭제 위험).
- Review → `comment`(String?) 또는 `rating`(Int)

**seed 부담(FK 차이):**
- Payment: `payment_payments.invoice_id`가 `payment_invoices(id)` FK → 테스트가 invoice 1건 선seed
  (jdbc insert into `payment_invoices` 또는 `InvoiceJpaRepository.save`) 후 그 id로 Payment 생성.
- Delivery·Review: 모듈 간 참조가 평범한 BIGINT(**FK 없음**) → 임의 Long id로 엔티티만 저장(선행 seed 불요).
- seed 헬퍼는 각 엔티티 필수 필드를 채우는 최소 팩토리(테스트 내 private fun).

### 4.3 테스트 격리 — `TestFixtures.truncateAll` 보강
현재 `truncateAll`은 `review_reviews`를 지우지 않는다(누락). Review 테스트 seed가 다음 테스트로 새지
않도록 자식-부모 순서로 추가: `DELETE FROM review_media;`(자식, 정확 테이블명은 구현 시 확인) →
`DELETE FROM review_reviews;`. (`review_media`는 `ON DELETE CASCADE`라 부모만 지워도 되지만 기존
명시-DELETE 스타일과 일관성 유지.)

### 4.4 teeth 확인
구현 후, 한 엔티티에서 `@Version`을 임시 제거하면 해당 테스트가 RED(예외 미발생)가 됨을 확인 후 원복.

## 5. 검증

- JDK21로 `:carry-app:test`(Testcontainers) GREEN — Flyway V20~V22 적용 + ddl-auto:validate 통과 +
  낙관락 3건 통과. JUnit XML로 `ConcurrencyIntegrationTest` 카운트(기존 + 3) 확인.
- 영향 모듈 컴파일: `:carry-payment:`, `:carry-delivery:`, `:carry-review:` compile 통과.

## 6. 영향 범위

| 파일 | 변경 |
|---|---|
| `PaymentJpaEntity.kt` / `DeliveryJpaEntity.kt` / `ReviewJpaEntity.kt` | `@Version var version` 추가(+import) |
| `V20__add_version_to_payments.sql` 등 3개(각 모듈 migration 디렉터리) | 신규 |
| `ConcurrencyIntegrationTest.kt` | JpaRepository·TransactionTemplate 주입 + 낙관락 충돌 테스트 3건 추가 |
| `carry-app/.../test/TestFixtures.kt` | `truncateAll`에 `review_media`/`review_reviews` DELETE 추가 |
| 도메인 모델·매퍼·`GlobalExceptionHandler` | 무변경 |
