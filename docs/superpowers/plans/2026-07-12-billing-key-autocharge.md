# 빌링키 자동과금 · 결제/물리 흐름 완전 분리 — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 결제(빌링키 자동과금)를 주문 물리 흐름에서 완전히 분리한다 — 주문은 결제 때문에 멈추지 않는다.

**Architecture:** carry-payment에 빌링키 도메인과 자동과금 사가(InvoiceIssued 자체 소비 → chargeBilling)를 신설하고, carry-order의 OrderStatus를 물리 상태 6개로 축소하며, carry-delivery의 completeDelivery 결제 게이트를 제거한다. 과금 실패는 ChargeRetrySweeper(백오프)·OverdueSweeper(72h 연체)·신규 주문 차단으로 흡수한다.

**Tech Stack:** Kotlin/Spring Boot 모듈러 모놀리스, Kafka(CDC-from-outbox) 코레오그래피 사가, JPA+Flyway(PostgreSQL/Testcontainers), ShedLock, Resilience4j.

**Spec:** `docs/superpowers/specs/2026-07-12-billing-key-autocharge-design.md`

**공통 규칙:**
- 빌드는 JDK 21 필요 (`gradle.properties`의 `org.gradle.java.home` 로컬 설정 유지, 커밋 금지).
- ⚠️ Gradle은 `--tests` 매칭 0건이어도 BUILD SUCCESSFUL을 낸다. 모든 테스트 스텝은 `build/test-results/test/*.xml`의 `tests="N" failures="0"`으로 검증할 것.
- 커밋 메시지는 conventional prefix(영어) + 한국어 본문. PowerShell에서 커밋할 땐 메시지를 파일로 만들어 `git commit -F` 사용.
- 브랜치: `feat/billing-key-autocharge` (스펙이 이미 커밋되어 있음).

---

## Chunk 1: carry-payment — PG 포트 개편 + 빌링키 도메인

### Task 1: PaymentGatewayPort 개편 (requestPayment 제거, 빌링 메서드 추가)

**Files:**
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/outbound/PaymentGatewayPort.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/stub/StubPgProviderAdapter.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/resilience/CircuitBreakerPaymentGateway.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/service/PaymentCommandService.kt` (requestPayment 제거)
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/PaymentCommandUseCase.kt` (시그니처·RequestPaymentCommand 제거)
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/inbound/rest/PaymentController.kt` + `dto/PaymentWebDto.kt`
- Modify: `carry-app/src/test/kotlin/com/carry/app/test/FakePgProviderAdapter.kt`
- Modify(파킹): `carry-app/src/test/kotlin/com/carry/app/saga/` 의 `OrderSagaIntegrationTest.kt`, `PaymentSagaIntegrationTest.kt`, `SettlementLedgerIntegrationTest.kt`, `PaymentReconciliationIntegrationTest.kt`
- Test: `carry-payment/src/test/kotlin/com/carry/payment/adapter/outbound/stub/StubPgProviderAdapterTest.kt`, `PaymentCommandServiceTest.kt`, `CircuitBreakerPaymentGatewayTest.kt`

주의: 이 태스크의 Step 4(수동 결제 경로 제거)와 Step 6(carry-app 테스트 파킹)까지 **같은 커밋**에 반영해야 전 모듈 컴파일이 유지된다.

- [ ] **Step 1: 포트 인터페이스 개편**

`PaymentGatewayPort.kt`에서 `PgPaymentRequest`를 삭제하고 아래로 대체:

```kotlin
data class PgBillingKeyRequest(
    val authKey: String,
    val customerKey: String,
)

data class PgBillingKeyResult(
    val success: Boolean,
    val billingKey: String? = null,
    val cardCompany: String? = null,
    val cardLast4: String? = null,
    val failReason: String? = null,
)

data class PgBillingChargeRequest(
    val billingKey: String,
    val customerKey: String,
    val orderId: Long,
    val amount: Long,
    val orderName: String,
    /** PG 측 dedup 멱등키 — `charge-{invoiceId}` 로 고정, 재시도에도 동일 값 재전달. */
    val idempotencyKey: String,
)
```

인터페이스는:

```kotlin
interface PaymentGatewayPort {
    /** 빌링키 발급 — 결제 발생 없음. authKey는 프론트 SDK 카드 등록창 결과(mock에선 임의 문자열). */
    fun issueBillingKey(request: PgBillingKeyRequest): PgBillingKeyResult

    /** 빌링키 자동과금 — 사용자 액션 없이 서버 단독 호출. */
    fun chargeBilling(request: PgBillingChargeRequest): PgPaymentResult

    fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult

    fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord>
}
```

`PgPaymentResult`, `PgCancelResult`, `PgTransactionType`, `PgTransactionRecord`는 그대로 유지.

- [ ] **Step 2: StubPgProviderAdapter 개편**

`requestPayment` 구현을 삭제하고 두 메서드 추가. 기존 in-memory `CopyOnWriteArrayList<PgTransactionRecord>`·dedup 구조 유지:

```kotlin
override fun issueBillingKey(request: PgBillingKeyRequest): PgBillingKeyResult {
    // 로컬 개발용 결정적 실패 마커: authKey가 "fail-"로 시작하면 발급 거절
    if (request.authKey.startsWith("fail-")) {
        return PgBillingKeyResult(success = false, failReason = "STUB: 카드 등록 거절 시뮬레이션")
    }
    return PgBillingKeyResult(
        success = true,
        billingKey = "STUB-BILLKEY-${request.customerKey}",
        cardCompany = "STUB카드",
        cardLast4 = "0000",
    )
}

override fun chargeBilling(request: PgBillingChargeRequest): PgPaymentResult {
    val pgTransactionId = "STUB-${request.orderId}-${request.idempotencyKey}"
    // 멱등: 같은 idempotencyKey 재호출은 기존 성공 결과 반환, CHARGE 중복 기록 없음
    if (transactions.none { it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CHARGE }) {
        transactions.add(PgTransactionRecord(pgTransactionId, PgTransactionType.CHARGE, request.amount, clock.instant()))
    }
    return PgPaymentResult(success = true, pgTransactionId = pgTransactionId)
}
```

(기존 `requestPayment`의 dedup·기록 방식을 그대로 이식. `clock` 의존이 없다면 기존 파일이 시각을 만들던 방식을 따른다.)

- [ ] **Step 3: CircuitBreakerPaymentGateway 위임 메서드 갱신**

`requestPayment` 위임을 삭제하고 `issueBillingKey`/`chargeBilling` 위임 추가 — 기존 `cancelPayment` 위임과 동일하게 CB `executeSupplier`로 감싼다.

- [ ] **Step 4: 수동 결제 경로 제거**

`PaymentCommandService.kt`에서 `requestPayment` 메서드 전체(47–146행)와 `PaymentCommandUseCase`의 해당 시그니처·`RequestPaymentCommand`, `PaymentController`의 `POST /pay` 엔드포인트를 **어노테이션 포함 39–60행**으로 삭제(44행부터만 지우면 고아 `@Operation`이 다음 선언에 붙어 컴파일 깨짐), `PaymentWebDto.kt`의 `PaymentRequest`(12–17행) 삭제. `markRefundPending`/`executeRefund`/`confirmRefundFromPg`/`completeRefund`는 유지.

- [ ] **Step 5: FakePgProviderAdapter(carry-app 테스트) 개편**

`shouldSucceed` 토글·`extraTransactions`·`reset()` 구조를 유지한 채 `requestPayment` → `issueBillingKey`+`chargeBilling`으로 교체. `issueBillingKey`는 **항상 성공**(`"FAKE-BILLKEY-{customerKey}"`, 카드 "FAKE카드"/"1234") — 실패 시뮬레이션은 과금에만 둔다(재등록 복구 시나리오가 등록 성공을 전제하므로). `chargeBilling`은 `shouldSucceed=false`면 `PgPaymentResult(success=false, failReason="FAKE: 과금 실패")`, 성공이면 Stub과 같은 규칙으로 CHARGE 기록.

- [ ] **Step 6: requestPayment 를 쓰는 carry-app 통합 테스트 파킹**

`OrderSagaIntegrationTest`, `PaymentSagaIntegrationTest`, `SettlementLedgerIntegrationTest`, `PaymentReconciliationIntegrationTest`의 `paymentCommandService.requestPayment(...)` 호출 라인을 삭제하고(대체 호출 없이 — 이 시점엔 AutoChargeService 미존재), 영향을 받는 테스트 클래스에 `@Disabled("Task 14에서 자동과금 경로로 재작성")` 부착. 목적은 **컴파일 유지**다 — 전 태스크 커밋 경계에서 저장소 전체가 컴파일되어야 한다는 원칙의 첫 적용 지점.

- [ ] **Step 7: 기존 단위 테스트 갱신 후 실행**

`StubPgProviderAdapterTest.kt`를 새 메서드 기준으로 재작성: ⓐ 발급 성공·결정적 billingKey, ⓑ `fail-` authKey 거절, ⓒ 과금 성공 + CHARGE 기록, ⓓ 같은 멱등키 재과금은 중복 기록 없음, ⓔ cancelPayment 기존 케이스 유지. `PaymentCommandServiceTest.kt`에서 requestPayment 관련 케이스 삭제(환불 케이스는 유지). `CircuitBreakerPaymentGatewayTest.kt`를 새 메서드 위임 기준으로 갱신.

Run: `./gradlew :carry-payment:test :carry-app:compileTestKotlin`
Expected: BUILD SUCCESSFUL — 반드시 `carry-payment/build/test-results/test/*.xml`에서 tests>0, failures=0 확인. `:carry-app:compileTestKotlin` 통과로 파킹 완결 확인.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: PG 포트를 빌링키 모델로 개편 — requestPayment 제거, issueBillingKey/chargeBilling 신설"
```

### Task 2: BillingKey 도메인 모델

**Files:**
- Create: `carry-payment/src/main/kotlin/com/carry/payment/domain/model/BillingKey.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/domain/vo/PaymentEnums.kt` (BillingKeyStatus 추가)
- Test: `carry-payment/src/test/kotlin/com/carry/payment/domain/model/BillingKeyTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.carry.payment.domain.model

import com.carry.payment.domain.vo.BillingKeyStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class BillingKeyTest {
    private val now = Instant.parse("2026-07-12T00:00:00Z")

    private fun create() = BillingKey.create(
        customerId = 1L, customerKey = "ck-uuid", billingKey = "bk-secret",
        cardCompany = "STUB카드", cardLast4 = "0000", now = now,
    )

    @Test
    fun `생성 시 ACTIVE 상태다`() {
        assertThat(create().status).isEqualTo(BillingKeyStatus.ACTIVE)
    }

    @Test
    fun `invalidate 하면 INVALID 전이 + 시각 기록`() {
        val key = create()
        key.invalidate(now.plusSeconds(60))
        assertThat(key.status).isEqualTo(BillingKeyStatus.INVALID)
        assertThat(key.invalidatedAt).isEqualTo(now.plusSeconds(60))
    }

    @Test
    fun `이미 INVALID 인 키를 다시 invalidate 하면 예외`() {
        val key = create()
        key.invalidate(now)
        assertThatThrownBy { key.invalidate(now) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `card last4는 4자리가 아니면 생성 거부`() {
        assertThatThrownBy {
            BillingKey.create(1L, "ck", "bk", "카드", "00000", now)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :carry-payment:test --tests "com.carry.payment.domain.model.BillingKeyTest"`
Expected: 컴파일 실패 (BillingKey 미존재).

- [ ] **Step 3: 구현**

`PaymentEnums.kt`에 추가:

```kotlin
/** 빌링키 상태 — 재등록 시 기존 ACTIVE 키를 INVALID 로 전환한다(고객당 활성 키 1개). */
enum class BillingKeyStatus {
    ACTIVE, INVALID,
}
```

`BillingKey.kt` (Payment.kt의 private constructor + factory 패턴을 따름):

```kotlin
package com.carry.payment.domain.model

import com.carry.payment.domain.vo.BillingKeyStatus
import java.time.Instant

/**
 * 고객의 자동결제 수단. billingKey 는 유출 시 임의 과금이 가능한 크리덴셜 —
 * 영속화 시 반드시 암호화(BillingKeyCryptoConverter)된다.
 * customerKey 는 PG 에 전달하는 고객 식별자로, 추측 불가능한 랜덤(UUID)이어야 한다.
 */
class BillingKey private constructor(
    val id: Long?,
    val customerId: Long,
    val customerKey: String,
    val billingKey: String,
    val cardCompany: String,
    val cardLast4: String,
    private var _status: BillingKeyStatus,
    private var _invalidatedAt: Instant?,
    val createdAt: Instant,
) {
    val status: BillingKeyStatus get() = _status
    val invalidatedAt: Instant? get() = _invalidatedAt

    fun invalidate(now: Instant) {
        check(_status == BillingKeyStatus.ACTIVE) { "이미 무효화된 빌링키입니다: id=$id" }
        _status = BillingKeyStatus.INVALID
        _invalidatedAt = now
    }

    companion object {
        fun create(
            customerId: Long, customerKey: String, billingKey: String,
            cardCompany: String, cardLast4: String, now: Instant,
        ): BillingKey {
            require(cardLast4.length == 4) { "cardLast4 는 4자리여야 합니다" }
            return BillingKey(null, customerId, customerKey, billingKey, cardCompany, cardLast4,
                BillingKeyStatus.ACTIVE, null, now)
        }

        fun reconstitute(
            id: Long, customerId: Long, customerKey: String, billingKey: String,
            cardCompany: String, cardLast4: String, status: BillingKeyStatus,
            invalidatedAt: Instant?, createdAt: Instant,
        ): BillingKey = BillingKey(id, customerId, customerKey, billingKey, cardCompany, cardLast4,
            status, invalidatedAt, createdAt)
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :carry-payment:test --tests "com.carry.payment.domain.model.BillingKeyTest"`
Expected: XML에서 tests=4, failures=0.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: BillingKey 도메인 모델 — ACTIVE/INVALID 생애주기"
```

### Task 3: 빌링키 영속화 — V26 마이그레이션 + AES-GCM 컨버터 + JPA

**Files:**
- Create: `carry-payment/src/main/resources/db/migration/V26__create_customer_billing_keys.sql`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/crypto/BillingKeyCryptoConverter.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/entity/BillingKeyJpaEntity.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/repository/BillingKeyJpaRepository.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/port/outbound/BillingKeyPersistencePort.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/BillingKeyPersistenceAdapter.kt`
- Test: `carry-payment/src/test/kotlin/com/carry/payment/adapter/outbound/persistence/crypto/BillingKeyCryptoConverterTest.kt`

- [ ] **Step 1: V26 마이그레이션 작성**

```sql
-- 고객 자동결제 수단. billing_key 는 애플리케이션 레벨 AES-GCM 암호문(Base64) 저장.
-- customer_key 는 PG 전달용 랜덤 식별자 — DB PK 재사용 금지 정책의 물리화.
CREATE TABLE customer_billing_keys (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT       NOT NULL,
    customer_key  VARCHAR(64)  NOT NULL,
    billing_key   VARCHAR(512) NOT NULL,
    card_company  VARCHAR(50)  NOT NULL,
    card_last4    VARCHAR(4)   NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    invalidated_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- 고객당 활성 키 1개 불변식
CREATE UNIQUE INDEX uq_billing_keys_active_per_customer
    ON customer_billing_keys (customer_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_billing_keys_customer ON customer_billing_keys (customer_id);
```

- [ ] **Step 2: 컨버터 실패 테스트 작성**

```kotlin
package com.carry.payment.adapter.outbound.persistence.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class BillingKeyCryptoConverterTest {
    // 32바이트 테스트 키
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val converter = BillingKeyCryptoConverter(key)

    @Test
    fun `암호화-복호화 왕복이 원문을 보존한다`() {
        val cipher = converter.convertToDatabaseColumn("bk-secret-123")
        assertThat(cipher).isNotEqualTo("bk-secret-123")
        assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo("bk-secret-123")
    }

    @Test
    fun `같은 평문도 IV 랜덤으로 매번 다른 암호문`() {
        val a = converter.convertToDatabaseColumn("bk")
        val b = converter.convertToDatabaseColumn("bk")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `null 은 null 로 통과`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :carry-payment:test --tests "*BillingKeyCryptoConverterTest"`
Expected: 컴파일 실패.

- [ ] **Step 4: 컨버터 구현**

```kotlin
package com.carry.payment.adapter.outbound.persistence.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * billing_key 컬럼 AES-256-GCM 암호화. 포맷: Base64(IV(12) || ciphertext+tag).
 * 키는 carry.payment.billing-key-enc-key (Base64 32바이트). 운영에선 환경변수 주입 전제 —
 * 코드 기본값은 로컬·테스트 전용이다.
 * Spring Boot 는 Hibernate SpringBeanContainer 를 자동 구성하므로 @Component 컨버터에 생성자 주입이 동작한다.
 */
@Component
@Converter
class BillingKeyCryptoConverter(
    @Value("\${carry.payment.billing-key-enc-key:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=}")
    keyBase64: String,
) : AttributeConverter<String?, String?> {

    private val key = SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES")
    private val random = SecureRandom()

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val bytes = Base64.getDecoder().decode(dbData)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
```

- [ ] **Step 5: 컨버터 테스트 통과 확인**

Run: `./gradlew :carry-payment:test --tests "*BillingKeyCryptoConverterTest"`
Expected: XML에서 tests=3, failures=0.

- [ ] **Step 6: 엔티티·리포지토리·포트·어댑터**

`BillingKeyJpaEntity.kt` — `PaymentJpaEntity`와 같은 BaseEntity 상속·`toDomain/fromDomain/updateFrom` 패턴. 핵심:

```kotlin
@Entity
@Table(name = "customer_billing_keys")
class BillingKeyJpaEntity(
    @Column(nullable = false) val customerId: Long,
    @Column(nullable = false, length = 64) val customerKey: String,
    @Convert(converter = BillingKeyCryptoConverter::class)
    @Column(nullable = false, length = 512) val billingKey: String,
    @Column(nullable = false, length = 50) val cardCompany: String,
    @Column(nullable = false, length = 4) val cardLast4: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    var status: BillingKeyStatus,
    var invalidatedAt: Instant?,
) : BaseEntity() { /* toDomain / fromDomain / updateFrom — Payment 패턴 준용 */ }
```

`BillingKeyJpaRepository`: `findFirstByCustomerIdAndStatus(customerId: Long, status: BillingKeyStatus): BillingKeyJpaEntity?`, `existsByCustomerIdAndStatus(customerId: Long, status: BillingKeyStatus): Boolean`.

`BillingKeyPersistencePort` (재등록 flush 순서 강제를 위해 `saveAndFlush` 포함 — 근거는 Task 4 register 주석):

```kotlin
interface BillingKeyPersistencePort {
    fun save(billingKey: BillingKey): BillingKey
    fun saveAndFlush(billingKey: BillingKey): BillingKey
    fun findActiveByCustomerId(customerId: Long): BillingKey?
    fun existsActiveByCustomerId(customerId: Long): Boolean
}
```

`BillingKeyPersistenceAdapter` — `PaymentPersistenceAdapter`와 동일한 getReferenceById+updateFrom 갱신 패턴.

- [ ] **Step 7: 전체 컴파일·모듈 테스트 확인 후 Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: 빌링키 영속화 — V26 테이블, AES-GCM 컨버터, 활성 키 부분 유니크"
```

### Task 4: 빌링키 등록 서비스 + REST API

**Files:**
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/BillingKeyUseCase.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/service/BillingKeyService.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/adapter/inbound/rest/BillingKeyController.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/inbound/rest/dto/PaymentWebDto.kt`
- Modify: `carry-common/src/main/kotlin/com/carry/common/exception/ErrorCode.kt`
- Modify: `carry-audit/src/main/kotlin/com/carry/audit/domain/AuditAction.kt` (BILLING_KEY_REGISTERED 추가 — 현재 enum에 없음, 무조건 추가)
- Test: `carry-payment/src/test/kotlin/com/carry/payment/application/service/BillingKeyServiceTest.kt`

- [ ] **Step 1: ErrorCode 추가**

`ErrorCode.kt` Payment 블록에 추가:

```kotlin
BILLING_KEY_ISSUE_FAILED(400, "Billing key issuance was rejected by PG"),
BILLING_KEY_NOT_FOUND(404, "Active billing key not found"),
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`BillingKeyServiceTest.kt` — mockk 또는 기존 서비스 테스트의 fake 스타일 준용. 케이스:

```kotlin
@Test fun `최초 등록 시 PG 발급 후 ACTIVE 로 저장한다`()
@Test fun `재등록 시 기존 ACTIVE 키를 INVALID 로 전환하고 새 키를 저장한다`()
@Test fun `PG 발급 거절 시 BILLING_KEY_ISSUE_FAILED 예외`()
@Test fun `customerKey 는 최초 등록 시 생성되고 재등록 시 재사용된다`()
@Test fun `활성 키 조회 - 없으면 BILLING_KEY_NOT_FOUND`()
```

핵심 검증: 재등록 케이스에서 `invalidate` 후 `save`가 기존 키·새 키 순서로 2회 호출, PG 호출은 `PgBillingKeyRequest(authKey, 재사용된 customerKey)`.

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :carry-payment:test --tests "*BillingKeyServiceTest"`
Expected: 컴파일 실패.

- [ ] **Step 4: 유스케이스·서비스 구현**

`BillingKeyUseCase.kt`:

```kotlin
interface BillingKeyUseCase {
    fun register(customerId: Long, authKey: String): BillingKey
    fun getActive(customerId: Long): BillingKey
}
```

`BillingKeyService.kt`:

```kotlin
@Service
class BillingKeyService(
    private val billingKeyPersistencePort: BillingKeyPersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val auditPort: AuditPort,
    private val clock: Clock,
) : BillingKeyUseCase {

    @Transactional
    override fun register(customerId: Long, authKey: String): BillingKey {
        val existing = billingKeyPersistencePort.findActiveByCustomerId(customerId)
        // customerKey 는 고객 최초 등록 시 1회 생성 — 이후 재등록에도 동일 키 재사용(토스 권장)
        val customerKey = existing?.customerKey ?: UUID.randomUUID().toString()

        val gateway = paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS)
        val result = gateway.issueBillingKey(PgBillingKeyRequest(authKey, customerKey))
        if (!result.success) {
            throw BusinessException(ErrorCode.BILLING_KEY_ISSUE_FAILED, result.failReason ?: "발급 거절")
        }

        val now = clock.instant()
        // ⚠️ 부분 유니크 인덱스 (customer_id) WHERE status='ACTIVE' 는 statement 단위로 검사된다.
        // Hibernate 기본 flush 순서는 INSERT→UPDATE 라, 무효화(UPDATE)와 신규(INSERT)를 한 flush 에
        // 묶으면 새 ACTIVE 행 INSERT 가 먼저 나가 제약 위반이 난다. saveAndFlush 로 무효화를 먼저 확정한다.
        existing?.let {
            it.invalidate(now)
            billingKeyPersistencePort.saveAndFlush(it)  // 포트에 saveAndFlush 추가 — 어댑터는 repository.saveAndFlush 위임
        }
        val saved = billingKeyPersistencePort.save(
            BillingKey.create(customerId, customerKey, result.billingKey!!,
                result.cardCompany ?: "UNKNOWN", result.cardLast4 ?: "0000", now)
        )
        // AuditPort 시그니처: record(action, targetType, targetId, before, after) — PaymentCommandService.kt:222 패턴
        auditPort.record(
            action = AuditAction.BILLING_KEY_REGISTERED,
            targetType = "BILLING_KEY",
            targetId = saved.id.toString(),
            before = existing?.id?.let { mapOf("invalidatedBillingKeyId" to it) },
            after = mapOf("customerId" to customerId, "cardLast4" to saved.cardLast4),
        )
        return saved
    }

    @Transactional(readOnly = true)
    override fun getActive(customerId: Long): BillingKey =
        billingKeyPersistencePort.findActiveByCustomerId(customerId)
            ?: throw BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND, "customerId=$customerId")
}
```

(`AuditAction.BILLING_KEY_REGISTERED`은 현재 enum에 없다 — `carry-audit/src/main/kotlin/com/carry/audit/domain/AuditAction.kt`의 `PAYMENT_REFUND` 옆에 추가한다.)

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :carry-payment:test --tests "*BillingKeyServiceTest"`
Expected: XML tests=5, failures=0.

- [ ] **Step 6: REST 컨트롤러 + DTO**

`PaymentWebDto.kt`에 추가:

```kotlin
data class BillingKeyRegisterRequest(
    @field:NotBlank val authKey: String,
)

data class BillingKeyResponse(
    val cardCompany: String,
    val cardLast4: String,
    val registeredAt: Instant,
) {
    companion object {
        fun from(key: BillingKey) = BillingKeyResponse(key.cardCompany, key.cardLast4, key.createdAt)
    }
}
```

`BillingKeyController.kt` — `PaymentController`의 `@AuthenticationPrincipal userId`·`ApiResponse` 패턴 준용:

```kotlin
@RestController
@RequestMapping("/api/v2/billing-keys")
class BillingKeyController(private val billingKeyUseCase: BillingKeyUseCase) {

    @PostMapping
    fun register(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: BillingKeyRegisterRequest,
    ): ResponseEntity<ApiResponse<BillingKeyResponse>> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(BillingKeyResponse.from(billingKeyUseCase.register(userId, request.authKey))))

    @GetMapping("/me")
    fun getMine(@AuthenticationPrincipal userId: Long): ApiResponse<BillingKeyResponse> =
        ApiResponse.success(BillingKeyResponse.from(billingKeyUseCase.getActive(userId)))
}
```

(응답은 마스킹 정보만 — billingKey·customerKey는 절대 응답에 넣지 않는다. Swagger 어노테이션은 기존 컨트롤러 수준으로.)

- [ ] **Step 7: 모듈 테스트 + Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: 빌링키 등록 API — 재등록 시 기존 키 무효화, customerKey 재사용"
```

---

## Chunk 2: carry-payment — 자동과금 사가 + 스위퍼 + 취소 보강

### Task 5: V27 마이그레이션 + Payment 재시도 필드 + Invoice OVERDUE

**Files:**
- Create: `carry-payment/src/main/resources/db/migration/V27__payment_retry_and_invoice_overdue.sql`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/domain/vo/PaymentEnums.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/domain/model/Payment.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/domain/model/Invoice.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/outbound/persistence/entity/PaymentJpaEntity.kt` (+ 어댑터의 toDomain/fromDomain/updateFrom)
- Test: `carry-payment/src/test/kotlin/com/carry/payment/domain/model/PaymentTest.kt`, `InvoiceTest.kt`

- [ ] **Step 1: 마이그레이션**

```sql
ALTER TABLE payment_payments
    ADD COLUMN retry_count  INT         NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ;

-- 기존 FAILED 결제 백필: NULL 이면 새 스위퍼가 영원히 집어가지 않는다.
-- 재시도 제외(취소 주문 등)는 스위퍼의 인보이스 상태 가드가 담당하므로 무조건 백필.
UPDATE payment_payments SET next_retry_at = NOW() WHERE status = 'FAILED';

CREATE INDEX idx_payments_failed_retry
    ON payment_payments (next_retry_at) WHERE status = 'FAILED';

-- 인보이스 OVERDUE 는 VARCHAR 상태 컬럼이라 스키마 변경 불필요(체크 제약 없음 확인됨).
```

(마이그레이션 전 `payment_invoices.status`에 CHECK 제약이 있는지 V8 마이그레이션에서 확인 — 있다면 제약 재정의 추가.)

- [ ] **Step 2: 실패하는 도메인 테스트 추가**

`InvoiceTest.kt`에 추가:

```kotlin
@Test fun `ISSUED 에서 OVERDUE 로 전이할 수 있다`()
@Test fun `OVERDUE 에서 PAID 로 전이할 수 있다`()
@Test fun `OVERDUE 에서 CANCELLED 로 전이할 수 있다`()
@Test fun `PAID 에서 OVERDUE 는 불가`()
```

`PaymentTest.kt`에 추가:

```kotlin
@Test fun `scheduleRetry 는 retryCount 증가 + nextRetryAt 설정`()
@Test fun `markRetrying 은 FAILED 에서만 PENDING 재전이`()
@Test fun `백오프 스케줄 - 1h, 4h, 12h, 24h 이후 24h 고정`()
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :carry-payment:test --tests "*InvoiceTest" --tests "*PaymentTest"`
Expected: 컴파일 실패.

- [ ] **Step 4: 구현**

`InvoiceStatus` 개편:

```kotlin
enum class InvoiceStatus {
    ISSUED, PAID, OVERDUE, CANCELLED, REFUNDED;

    fun canTransitionTo(target: InvoiceStatus): Boolean = when (this) {
        ISSUED -> target in listOf(PAID, OVERDUE, CANCELLED)
        // OVERDUE: 미수금 확정(신규 주문 차단) — 재과금은 계속되므로 PAID 로 회복 가능
        OVERDUE -> target in listOf(PAID, CANCELLED)
        PAID -> target == REFUNDED
        CANCELLED -> false
        REFUNDED -> false
    }
}
```

`Invoice.kt`에 `markOverdue()` 추가(기존 `markPaid/cancel/refund`와 동일한 `transitTo` 사용).

`Payment.kt`에 필드·메서드 추가:

```kotlin
private var _retryCount: Int = 0
private var _nextRetryAt: Instant? = null
val retryCount: Int get() = _retryCount
val nextRetryAt: Instant? get() = _nextRetryAt

/** 과금 실패 후 다음 재시도 예약. 백오프: 1h → 4h → 12h → 24h → 이후 24h 고정. */
fun scheduleRetry(now: Instant) {
    // 도메인 규약: plain check 대신 checkState(→BusinessException→4xx). Payment.kt 기존 전이 가드와 동일 스타일로.
    checkState(_status == PaymentStatus.FAILED) { "FAILED 상태에서만 재시도를 예약할 수 있습니다" }
    _retryCount += 1
    _nextRetryAt = now.plus(backoffFor(_retryCount))
}

/** 스위퍼가 재과금 직전 호출 — FAILED → PENDING 재전이(기존 전이 규칙에 존재). */
fun markRetrying() {
    transitTo(PaymentStatus.PENDING)
    _nextRetryAt = null
}

companion object {
    // 의도적 스펙 이탈 기록: 스펙 §5.2 는 "값은 설정으로 외부화"라 했으나, 백오프 스케줄은
    // 도메인 규칙(순수 함수)이라 도메인에 상수로 두고 스윕 주기·연체 임계만 설정으로 뺀다.
    private val BACKOFF = listOf(
        Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(12), Duration.ofHours(24),
    )
    fun backoffFor(retryCount: Int): Duration = BACKOFF.getOrElse(retryCount - 1) { Duration.ofHours(24) }
}
```

`create`/`reconstitute`/`PaymentJpaEntity`/어댑터에 두 필드 배선.

- [ ] **Step 5: 통과 확인 + Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: Payment 재시도 필드(백오프) + Invoice OVERDUE 상태 — V27"
```

### Task 6: AutoChargeService — 인보이스 발행 즉시 자동과금

**Files:**
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/service/AutoChargeService.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/PaymentSagaEventHandler.kt` (onInvoiceIssued 추가)
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/adapter/inbound/kafka/PaymentEventConsumer.kt`
- Test: `carry-payment/src/test/kotlin/com/carry/payment/application/service/AutoChargeServiceTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`AutoChargeServiceTest.kt` 케이스 (기존 `PaymentCommandServiceTest`의 fake/mock 스타일 준용):

```kotlin
@Test fun `과금 성공 - Payment COMPLETED, Invoice PAID, 원장 기입, PaymentCompletedEvent 발행`()
@Test fun `과금 실패 - Payment FAILED, 재시도 예약, PaymentFailedEvent 발행(최초 1회)`()
@Test fun `활성 빌링키 없음 - PG 호출 없이 FAILED + 재시도 예약 + 이벤트 발행`()
@Test fun `이미 결제가 존재하는 인보이스는 skip - 중복 이벤트 멱등`()
@Test fun `ISSUED 아닌 인보이스(CANCELLED)는 skip`()
@Test fun `멱등키는 charge-{invoiceId} 로 PG 에 전달된다`()
@Test fun `재시도 성공 - FAILED 결제를 PENDING 재전이 후 COMPLETED, OVERDUE 인보이스는 PAID 회복`()
@Test fun `재시도 실패 - 이벤트 재발행 없이 다음 재시도만 예약`()
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :carry-payment:test --tests "*AutoChargeServiceTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

```kotlin
@Service
class AutoChargeService(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val invoicePersistencePort: InvoicePersistencePort,
    private val billingKeyPersistencePort: BillingKeyPersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val eventPublisher: EventPublisherPort,
    private val ledgerPort: LedgerPort,
    private val orderStateQueryPort: OrderStateQueryPort,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
) {

    /** InvoiceIssuedEvent 소비 진입점 — 물리 흐름과 완전히 독립인 결제 사가의 시작. */
    @Transactional
    fun chargeInvoice(invoiceId: Long) {
        val invoice = invoicePersistencePort.findById(invoiceId) ?: return
        if (invoice.status !in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) return
        // 중복 이벤트 멱등: 이 인보이스의 결제가 이미 있으면 skip (재시도는 retryCharge 전용)
        if (paymentPersistencePort.findByOrderId(invoice.orderId)?.invoiceId == invoice.id) return

        val payment = Payment.create(invoice.id!!, invoice.orderId, invoice.customerId,
            PgProvider.TOSS_PAYMENTS, invoice.totalAmount, clock.instant())
        attemptCharge(paymentPersistencePort.save(payment), invoice, isFirstAttempt = true)
    }

    /** ChargeRetrySweeper 진입점. */
    @Transactional
    fun retryCharge(paymentId: Long) {
        val payment = paymentPersistencePort.findById(paymentId) ?: return
        if (payment.status != PaymentStatus.FAILED) return
        val invoice = invoicePersistencePort.findById(payment.invoiceId) ?: return
        // 취소된 인보이스는 재시도 제외 — next_retry_at 이 아니라 이 가드가 제외 기제다
        if (invoice.status !in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) return
        payment.markRetrying()
        attemptCharge(payment, invoice, isFirstAttempt = false)
    }

    private fun attemptCharge(payment: Payment, invoice: Invoice, isFirstAttempt: Boolean) {
        val billingKey = billingKeyPersistencePort.findActiveByCustomerId(invoice.customerId)
        if (billingKey == null) {
            handleFailure(payment, "활성 빌링키 없음", isFirstAttempt)
            return
        }
        val gateway = paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS)
        val result = gateway.chargeBilling(PgBillingChargeRequest(
            billingKey = billingKey.billingKey,
            customerKey = billingKey.customerKey,
            orderId = invoice.orderId,
            amount = invoice.totalAmount,
            orderName = "세탁 서비스 (${invoice.weight}kg)",
            idempotencyKey = "charge-${invoice.id}",
        ))
        if (result.success) {
            payment.markCompleted(result.pgTransactionId!!, clock.instant())
            val saved = paymentPersistencePort.save(payment)
            invoice.markPaid()
            invoicePersistencePort.save(invoice)
            ledgerPort.record(LedgerEntries.forPayment(saved, invoice,
                orderStateQueryPort.findCarrierId(invoice.orderId)))
            eventPublisher.publish("Payment", invoice.orderId.toString(), "PaymentCompletedEvent",
                PaymentCompletedEvent(saved.id!!, invoice.orderId, invoice.id!!, saved.amount))
            metricsPort.incrementCounter("carry.payment.autocharge.success")
        } else {
            handleFailure(payment, result.failReason ?: "PG 과금 거절", isFirstAttempt)
        }
    }

    private fun handleFailure(payment: Payment, reason: String, isFirstAttempt: Boolean) {
        payment.markFailed(reason)
        payment.scheduleRetry(clock.instant())
        val saved = paymentPersistencePort.save(payment)
        // 알림 스팸 방지: 최초 실패만 이벤트 발행, 재시도 실패는 조용히 다음 예약만
        if (isFirstAttempt) {
            eventPublisher.publish("Payment", saved.orderId.toString(), "PaymentFailedEvent",
                PaymentFailedEvent(saved.id!!, saved.orderId, reason))
        }
        metricsPort.incrementCounter("carry.payment.autocharge.failed")
    }
}
```

(`markCompleted`가 PENDING 전제라면 `retryCharge`의 `markRetrying()`이 선행되므로 일관됨. `Payment.markFailed` 시그니처가 reason만 받는 현행 유지.)

- [ ] **Step 4: 이벤트 소비 배선 — payment 모듈의 자체 소비**

`PaymentSagaEventHandler` 인바운드 포트에 `fun onInvoiceIssued(event: InvoiceIssuedEvent)` 추가, `PaymentSagaHandler`에 구현(로그 컨텍스트 `SagaLogContext.withOrderId` 래핑 후 `autoChargeService.chargeInvoice(event.invoiceId)` 위임).

`PaymentEventConsumer.kt`에 세 번째 리스너 추가 — 기존 두 리스너와 동일한 구조:

```kotlin
@KafkaListener(topics = ["carry.Payment.events"], groupId = "carry-payment-module")
fun consumePaymentEvents(record: ConsumerRecord<String, String>) {
    // 자체 outbox 이벤트 소비(자동과금) — 이 코드베이스 최초의 자기 소비 리스너.
    // InvoiceIssuedEvent 외의 자기 이벤트(PaymentCompleted 등)는 라우팅 없이 무시된다.
    val envelope = /* 파일 내 기존 리스너와 동일한 역직렬화 */
    eventConsumerSupport.processIfNotDuplicate("carry-payment-module", envelope.id, envelope.traceId, envelope.eventType) {
        when (envelope.eventType) {
            "InvoiceIssuedEvent" -> sagaHandler.onInvoiceIssued(/* payload 파싱 */)
        }
    }
}
```

(역직렬화·dedup 호출은 파일 내 기존 리스너 2개를 그대로 복제해 topics·eventType 분기만 다르게 — 기존 리스너는 `record`만 받고 수동 ack 를 쓰지 않는다.)

- [ ] **Step 5: 통과 확인 + Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0. `PaymentSagaHandlerTest`에 onInvoiceIssued 위임 케이스 1개 추가.

```bash
git add -A && git commit -m "feat: 자동과금 사가 — InvoiceIssued 자체 소비, 최초 실패만 이벤트 발행"
```

### Task 7: ChargeRetrySweeper + OverdueSweeper

**Files:**
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/service/ChargeRetrySweeper.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/service/OverdueSweeper.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/outbound/PaymentPersistencePort.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/outbound/InvoicePersistencePort.kt`
- Test: `carry-payment/src/test/kotlin/com/carry/payment/application/service/ChargeRetrySweeperTest.kt`, `OverdueSweeperTest.kt`

- [ ] **Step 1: 포트 메서드 추가**

`PaymentPersistencePort`: `fun findRetryableFailed(now: Instant): List<Payment>` (구현: `findByStatusAndNextRetryAtBefore(FAILED, now)` 리포지토리 메서드).
`InvoicePersistencePort`: `fun findIssuedBefore(cutoff: Instant): List<Invoice>` (구현: `findByStatusAndCreatedAtBefore(ISSUED, cutoff)`), `fun existsOverdueByCustomerId(customerId: Long): Boolean` (구현: `existsByCustomerIdAndStatus(customerId, OVERDUE)`).

- [ ] **Step 2: 실패하는 테스트 작성**

`ChargeRetrySweeperTest.kt` (RefundRetrySweeperTest 스타일):

```kotlin
@Test fun `next_retry_at 도래한 FAILED 결제를 재과금한다`()
@Test fun `개별 건 실패가 다른 건 처리를 막지 않는다`()
@Test fun `도래하지 않은 건은 건드리지 않는다`()
```

`OverdueSweeperTest.kt`:

```kotlin
@Test fun `발행 72h 경과 ISSUED 인보이스를 OVERDUE 로 마킹한다`()
@Test fun `72h 미만은 건드리지 않는다`()
@Test fun `이미 PAID/CANCELLED 는 대상 아님 (쿼리 자체가 ISSUED 만)`()
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :carry-payment:test --tests "*SweeperTest"`
Expected: 컴파일 실패.

- [ ] **Step 4: 구현 — RefundRetrySweeper 패턴 복제**

```kotlin
@Component
class ChargeRetrySweeper(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val autoChargeService: AutoChargeService,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.payment.charge-retry-interval-ms:600000}")
    @SchedulerLock(name = "chargeRetrySweep", lockAtMostFor = "PT9M", lockAtLeastFor = "PT0S")
    fun retryFailedCharges() {
        paymentPersistencePort.findRetryableFailed(clock.instant()).forEach { payment ->
            try {
                autoChargeService.retryCharge(payment.id!!)
            } catch (e: Exception) {
                // chargeBilling throw(CB OPEN 등) 시 트랜잭션 롤백으로 next_retry_at 이 과거에 남아
                // 매 스윕(10분)마다 재시도된다 — PG 장애 중 RefundRetrySweeper 와 동일한 의도된 동작.
                log.warn("ChargeRetrySweeper: 재과금 실패 paymentId={}", payment.id, e)
                metricsPort.incrementCounter("carry.payment.charge_retry_failed")
            }
        }
    }
}
```

⚠️ **레이스 주의**: ChargeRetrySweeper가 동시에 같은 인보이스를 PAID로 만들 수 있다(ShedLock 이름이 달라 병행 실행됨). 도메인 로드 후 blind save 방식이면 PAID→OVERDUE lost-update로 "결제 완료인데 영구 주문 차단" 상태가 생긴다. 따라서 연체 마킹은 **조건부 UPDATE**로 구현한다.

`InvoiceJpaRepository`에 추가:

```kotlin
/** ISSUED 인 행만 OVERDUE 로 — 동시에 PAID 로 바뀐 인보이스를 덮어쓰지 않는 조건부 갱신. */
@Modifying
@Query("update InvoiceJpaEntity i set i.status = 'OVERDUE', i.updatedAt = :now where i.id = :id and i.status = 'ISSUED'")
fun markOverdueIfIssued(id: Long, now: Instant): Int
```

`InvoicePersistencePort`에 `fun markOverdueIfIssued(invoiceId: Long, now: Instant): Boolean` 추가(어댑터가 위 쿼리 위임, affected==1).

```kotlin
@Component
class OverdueSweeper(
    private val invoicePersistencePort: InvoicePersistencePort,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
    @Value("\${carry.payment.overdue-threshold-hours:72}") private val thresholdHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.payment.overdue-sweep-interval-ms:3600000}")
    @SchedulerLock(name = "overdueSweep", lockAtMostFor = "PT50M", lockAtLeastFor = "PT0S")
    fun markOverdueInvoices() {
        val now = clock.instant()
        val cutoff = now.minus(Duration.ofHours(thresholdHours))
        invoicePersistencePort.findIssuedBefore(cutoff).forEach { invoice ->
            try {
                if (invoicePersistencePort.markOverdueIfIssued(invoice.id!!, now)) {
                    log.warn("OverdueSweeper: 인보이스 연체 확정 invoiceId={} customerId={}", invoice.id, invoice.customerId)
                    metricsPort.incrementCounter("carry.payment.invoice_overdue")
                }
            } catch (e: Exception) {
                // 개별 건 실패가 배치 전체를 막지 않도록 — Refund/ChargeRetrySweeper 와 동일 원칙
                log.warn("OverdueSweeper: 연체 마킹 실패 invoiceId={}", invoice.id, e)
            }
        }
    }
}
```

테스트에 레이스 케이스 추가: `@Test fun `조회 후 PAID 로 바뀐 인보이스는 OVERDUE 로 덮어쓰지 않는다`()`.

- [ ] **Step 5: 통과 확인 + Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: ChargeRetrySweeper(백오프 재과금) + OverdueSweeper(72h 연체 확정)"
```

### Task 8: 취소 보강 + 주문 자격 조회 유스케이스 + isOrderPaid 제거

**Files:**
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/service/PaymentSagaHandler.kt`
- Create: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/BillingQueryUseCase.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/service/BillingKeyService.kt` (또는 별도 서비스에 구현)
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/PaymentQueryUseCase.kt` (isOrderPaid @Deprecated 마킹만 — 삭제는 Task 12)
- Test: `carry-payment/src/test/kotlin/com/carry/payment/application/service/PaymentSagaHandlerTest.kt`

주의: `PaymentSagaHandler`는 현재 `invoicePersistencePort`를 주입받지 않는다 — 생성자에 추가할 것.

- [ ] **Step 1: 실패하는 테스트 — onOrderCancelled 미과금 인보이스 분기**

`PaymentSagaHandlerTest.kt`에 추가:

```kotlin
@Test fun `주문 취소 시 미과금(ISSUED) 인보이스는 CANCELLED 로 전환된다`()
@Test fun `주문 취소 시 OVERDUE 인보이스도 CANCELLED 로 전환된다`()
@Test fun `주문 취소 시 COMPLETED 결제는 기존대로 환불 대기 마킹`()
@Test fun `인보이스도 결제도 없으면 조용히 skip`()
```

- [ ] **Step 2: 구현**

`PaymentSagaHandler.onOrderCancelled`에 인보이스 분기 추가 (기존 환불 분기 유지):

```kotlin
@Transactional
override fun onOrderCancelled(event: OrderCancelledEvent) {
    SagaLogContext.withOrderId(event.orderId) {
        val payment = paymentPersistencePort.findByOrderId(event.orderId)
        if (payment != null && payment.status == PaymentStatus.COMPLETED) {
            paymentCommandUseCase.markRefundPending(event.orderId)
            return@withOrderId
        }
        // 미과금 인보이스(ISSUED/OVERDUE)는 과금 중단 — 스위퍼의 인보이스 상태 가드가 재시도를 자연 배제
        val invoice = invoicePersistencePort.findByOrderId(event.orderId)
        if (invoice != null && invoice.status in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) {
            invoice.cancel()
            invoicePersistencePort.save(invoice)
            log.info("Payment saga: onOrderCancelled — 미과금 인보이스 취소 invoiceId={}", invoice.id)
        } else {
            log.info("Payment saga: onOrderCancelled — 환불/취소 대상 없음, skip")
        }
    }
}
```

(`Invoice.cancel()`이 OVERDUE→CANCELLED를 허용하는지 Task 5의 전이 규칙에서 이미 보장.)

- [ ] **Step 3: BillingQueryUseCase (주문 생성 전제조건용 인바운드)**

```kotlin
/** carry-order 의 주문 생성 전제조건 조회 — carry-app 어댑터가 위임한다. */
interface BillingQueryUseCase {
    fun hasActiveBillingKey(customerId: Long): Boolean
    fun hasOverdueInvoice(customerId: Long): Boolean
}
```

`BillingKeyService`가 구현: `hasActiveBillingKey` = `billingKeyPersistencePort.existsActiveByCustomerId`, `hasOverdueInvoice` = `invoicePersistencePort.existsOverdueByCustomerId`. 각 1줄 테스트 추가.

- [ ] **Step 4: isOrderPaid Deprecated 마킹 (실제 삭제는 Task 12)**

`PaymentQueryUseCase.isOrderPaid`에 `@Deprecated("배달 완료 결제 게이트 폐기 — Task 12에서 delivery 포트와 함께 삭제")` 부착만 한다. **이 시점에 삭제하면 안 된다** — delivery의 `completeDelivery` 게이트와 carry-app `PaymentQueryPortAdapter`가 아직 호출 중이라 컴파일이 깨진다. 실제 삭제는 Task 12에서 delivery 게이트·어댑터·testFixtures와 단일 커밋으로 수행한다.

- [ ] **Step 5: 통과 확인 + Commit**

Run: `./gradlew :carry-payment:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: 주문 취소 시 미과금 인보이스 취소 분기 + BillingQueryUseCase 신설"
```

---

## Chunk 3: order/delivery 분리 + 배선 + 통합 테스트 + 문서

### Task 9: OrderStatus 축소 + Order 도메인 정리

**Files:**
- Modify: `carry-order/src/main/kotlin/com/carry/order/domain/vo/OrderEnums.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/domain/model/Order.kt`
- Create: `carry-order/src/main/resources/db/migration/V29__order_status_physical_only.sql` (V28은 T6 견고화의 payment_invoice_hardening 이 차지)
- Modify: `carry-order/src/main/kotlin/com/carry/order/adapter/outbound/persistence/entity/OrderJpaEntity.kt` + 영속성 어댑터 (invoice_id/total_amount 매핑 제거)
- Modify: `carry-order/src/main/kotlin/com/carry/order/adapter/inbound/rest/dto/OrderWebDto.kt` (`OrderResponse.totalAmount` 제거 — 56·77행)
- Test: `carry-order/src/test/kotlin/com/carry/order/domain/vo/OrderStatusTest.kt`, `carry-order/src/test/kotlin/com/carry/order/domain/model/OrderTest.kt`

**API 계약 변경 결정**: `OrderResponse.totalAmount`는 삭제한다. 금액은 결제 모듈 API(`GET /api/v2/payments/{orderId}/invoice`)로 일원화 — 주문 응답에 결제 정보를 남기면 분리가 반쪽이 된다. 프론트 후속 사이클에서 인보이스 조회로 대체 (openapi 재생성은 Task 15).

- [ ] **Step 1: 실패하는 테스트 재작성 (테스트 먼저)**

`OrderStatusTest.kt` 전면 재작성:

```kotlin
@Test
fun `전체 Happy Path 상태 전이가 유효하다`() {
    val happyPath = listOf(
        OrderStatus.CREATED to OrderStatus.DISPATCHED,
        OrderStatus.DISPATCHED to OrderStatus.PICKED_UP,
        OrderStatus.PICKED_UP to OrderStatus.IN_PROGRESS,
        OrderStatus.IN_PROGRESS to OrderStatus.COMPLETED,
    )
    happyPath.forEach { (from, to) ->
        assertThat(from.canTransitionTo(to)).withFailMessage("$from → $to should be valid").isTrue()
    }
}

@Test fun `모든 진행 상태에서 CANCELLED 로 전이 가능하다 (COMPLETED 제외)`()
@Test fun `COMPLETED 와 CANCELLED 는 종결 상태다`()
@Test fun `고객은 CREATED, DISPATCHED 에서만 취소 가능하다`()      // isCancellableBy(CUSTOMER)
@Test fun `코디네이터와 시스템은 PICKED_UP, IN_PROGRESS 도 취소 가능하다`() // isCancellableBy(COORDINATOR/SYSTEM)
@Test fun `isForwardActive 는 COMPLETED, CANCELLED 만 false`()
```

`OrderTest.kt`: `markInvoiced` 케이스와 Compensation 이너 클래스 삭제, happy path 시퀀스를 `markDispatched → markPickedUp → markInProgress → markCompleted`로 갱신, 취소 케이스를 `isCancellableBy` 기준으로 재작성(고객의 PICKED_UP 취소 거부 케이스 포함).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :carry-order:test --tests "*OrderStatusTest" --tests "*OrderTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

`OrderEnums.kt`:

```kotlin
/** 주문 상태 — 물리 세계의 사실만 기술한다. 결제 생애주기는 carry-payment(Invoice/Payment) 소관. */
enum class OrderStatus {
    CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS, COMPLETED, CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP -> target in setOf(IN_PROGRESS, CANCELLED)
        IN_PROGRESS -> target in setOf(COMPLETED, CANCELLED)
        COMPLETED -> false
        CANCELLED -> false
    }

    /** 취소 가능 여부는 행위자에 따라 다르다 — 고객은 수거 전만, 코디/시스템은 완료 전까지. */
    fun isCancellableBy(by: CancelledBy): Boolean = when (by) {
        CancelledBy.CUSTOMER -> this in setOf(CREATED, DISPATCHED)
        CancelledBy.COORDINATOR, CancelledBy.SYSTEM ->
            this in setOf(CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS)
    }

    /** forward 사가 진행 중 여부 — 늦게 도착한 이벤트의 멱등 no-op 판정 술어. */
    fun isForwardActive(): Boolean = this !in setOf(COMPLETED, CANCELLED)
}
```

기존 `isCancellable()`은 삭제(호출부는 `isCancellableBy`로 이행).

`Order.kt`: `markInvoiced/markPaid/markPaymentFailed/markRefundPending/markRefunded` 삭제, `_invoiceId`/`_totalAmount` 필드·getter·`reconstitute` 파라미터 삭제, `cancel(reason, by, now)`가 `_status.isCancellableBy(by)`로 가드하도록 변경. JPA 엔티티·어댑터에서 invoice_id/total_amount 매핑 제거.

- [ ] **Step 4: V28 마이그레이션**

```sql
-- 결제 상태를 주문에서 추방 — 물리 상태로 매핑 (실데이터 없는 학습 프로젝트, dev 데이터만 해당)
-- PAID 도 PICKED_UP 로: 구 모델에서 PAID 는 세탁 시작(LaundryStarted) 전 단계라 물리적으로 PICKED_UP.
-- IN_PROGRESS 로 매핑하면 이후 LaundryStarted 가 IN_PROGRESS→IN_PROGRESS 무효 전이로 poison 된다.
UPDATE orders SET status = 'PICKED_UP' WHERE status IN ('INVOICED', 'PAYMENT_FAILED', 'PAID');
UPDATE orders SET status = 'CANCELLED' WHERE status IN ('REFUND_PENDING', 'REFUNDED');

ALTER TABLE orders DROP COLUMN IF EXISTS invoice_id;
ALTER TABLE orders DROP COLUMN IF EXISTS total_amount;
```

**StuckSagaDetector 임계 상향**: 결제 분리로 PICKED_UP·IN_PROGRESS 가 정상적으로 몇 시간 체류하므로 `carry.order.stuck-saga-threshold-hours` 기본값을 6→24 로 올린다(오탐 방지).

(주문 테이블 실명·컬럼명은 V5/V15/V17 마이그레이션에서 확인 후 맞출 것.)

- [ ] **Step 5: 커밋하지 않음 — Task 10과 단일 커밋**

⚠️ 이 시점에 `OrderSagaHandler`·`StuckSagaDetector`·`PaymentRetryDeadlineSweeper`·`OrderCommandService`가 삭제된 심볼을 참조해 **모듈 컴파일이 깨진 상태**다(테스트만 돌려도 main 컴파일이 선행되므로 실행 불가). 곧바로 Task 10을 진행해 호출부를 정리한 뒤 **Task 10 Step 3에서 함께 검증·커밋**한다.

### Task 10: 주문 모듈의 결제 결합 제거 (사가·소비자·스위퍼·감시자)

**Files:**
- Modify: `carry-order/src/main/kotlin/com/carry/order/application/service/OrderSagaHandler.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/adapter/inbound/kafka/OrderEventConsumer.kt`
- Delete: `carry-order/src/main/kotlin/com/carry/order/application/service/PaymentRetryDeadlineSweeper.kt`
- Delete: `carry-order/src/test/kotlin/com/carry/order/application/service/PaymentRetryDeadlineSweeperTest.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/application/service/StuckSagaDetector.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/application/service/OrderCommandService.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/application/port/inbound/OrderSagaEventHandler.kt`
- Test: `OrderSagaHandlerTest.kt`, `OrderCommandServiceTest.kt`, `StuckSagaDetectorTest.kt`, `OrderEventConsumerIdempotencyTest.kt`

- [ ] **Step 1: 제거 목록 실행**

- `OrderSagaHandler`: `onInvoiceIssued`/`onPaymentCompleted`/`onPaymentFailed`/`onRefundCompleted` 삭제 + 결제 이벤트 import 4종 삭제. `onLaundryStarted`는 유지 — 이제 PICKED_UP→IN_PROGRESS 전이가 됨(도메인 전이 규칙이 이미 허용).
- `OrderSagaEventHandler` 인바운드 포트에서 해당 시그니처 4종 삭제.
- `OrderEventConsumer`: `consumePaymentEvents` 리스너 전체(74–98행)와 결제 이벤트 import 삭제.
- `PaymentRetryDeadlineSweeper` + 테스트 파일 삭제.
- `StuckSagaDetector.WATCHED_STATUSES` → `setOf(CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS)`, KDoc의 PAYMENT_FAILED/PaymentRetryDeadlineSweeper 언급 정리.
- `OrderCommandService.cancelOrder`: PAID→REFUND_PENDING 분기(119–124행) 삭제 — 모든 취소는 `doCancel`+`publishOrderCancelled` 단일 경로. 도메인 `cancel`이 `isCancellableBy` 가드를 수행하므로 서비스는 액터만 전달.
- `OrderCoordinatorController.cancelOrder`의 Swagger 설명에서 "PAID→환불" 문구를 "수거 후 취소 시 결제 모듈이 환불/과금중단을 처리"로 갱신.

- [ ] **Step 2: 테스트 갱신**

- `OrderSagaHandlerTest`: 결제 이벤트 케이스 삭제. `onLaundryStarted` 케이스를 "PICKED_UP 주문이 IN_PROGRESS 로 전이"로 갱신.
- `OrderCommandServiceTest`: PAID 관련 케이스 삭제, "코디네이터가 PICKED_UP 주문을 취소하면 CANCELLED + 이벤트 발행", "고객의 PICKED_UP 취소는 ORDER_NOT_CANCELLABLE" 케이스 추가.
- `StuckSagaDetectorTest`: 감시 상태 목록 갱신.
- `OrderEventConsumerIdempotencyTest`: 결제 토픽 케이스가 있으면 삭제.

- [ ] **Step 3: 모듈 테스트 통과 확인 + Commit (Task 9 변경분 포함 단일 커밋)**

Run: `./gradlew :carry-order:test :carry-app:compileTestKotlin`
Expected: XML failures=0. (carry-app 통합 테스트의 `onInvoiceIssued`/`onPaymentCompleted` 호출부는 Task 1에서 @Disabled 파킹된 파일들 내부이지만 **컴파일은 되어야 하므로**, 삭제된 핸들러를 호출하는 라인이 파킹 파일에 남아 있으면 이 스텝에서 함께 삭제한다.)

```bash
git add -A && git commit -m "refactor!: OrderStatus 물리 상태 6개로 축소 + 주문 모듈 결제 결합 제거 — V28"
```

### Task 11: 주문 생성 전제조건 (빌링키 + 연체)

**Files:**
- Create: `carry-order/src/main/kotlin/com/carry/order/application/port/outbound/BillingQueryPort.kt`
- Modify: `carry-order/src/main/kotlin/com/carry/order/application/service/OrderCommandService.kt`
- Modify: `carry-common/src/main/kotlin/com/carry/common/exception/ErrorCode.kt`
- Create: `carry-order/src/testFixtures/kotlin/com/carry/order/application/port/outbound/contract/FakeBillingQueryPort.kt` (+ Contract, 기존 Fake 계약 패턴)
- Test: `carry-order/src/test/kotlin/com/carry/order/application/service/OrderCommandServiceTest.kt`

- [ ] **Step 1: ErrorCode 추가 + ORDER_NOT_PAID 제거 예약**

Order 블록에 추가:

```kotlin
BILLING_KEY_REQUIRED(409, "Active billing key is required to create an order"),
OVERDUE_INVOICE_EXISTS(409, "Customer has an overdue invoice"),
```

(`ORDER_NOT_PAID(402)` 삭제는 Task 12에서 delivery 게이트와 함께.)

- [ ] **Step 2: 실패하는 테스트 작성**

`OrderCommandServiceTest.kt`에 추가:

```kotlin
@Test fun `활성 빌링키가 없으면 주문 생성이 BILLING_KEY_REQUIRED 로 거부된다`()
@Test fun `연체 인보이스가 있으면 주문 생성이 OVERDUE_INVOICE_EXISTS 로 거부된다`()
@Test fun `전제조건 통과 시 주문이 정상 생성된다`()
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :carry-order:test --tests "*OrderCommandServiceTest"`
Expected: 컴파일 실패.

- [ ] **Step 4: 구현**

`BillingQueryPort.kt`:

```kotlin
package com.carry.order.application.port.outbound

/**
 * 주문 생성 전제조건 조회 (carry-payment 위임, carry-app 어댑터 배선).
 * 불변식: 존재하는 주문은 결제 때문에 멈추지 않는다 — 그 대가로 생성 시점에 지불수단을 확보한다.
 */
interface BillingQueryPort {
    fun hasActiveBillingKey(customerId: Long): Boolean
    fun hasOverdueInvoice(customerId: Long): Boolean
}
```

`OrderCommandService.createOrder`의 배송지 조회(58행) 직전에 삽입:

```kotlin
if (!billingQueryPort.hasActiveBillingKey(command.customerId)) {
    throw BusinessException(ErrorCode.BILLING_KEY_REQUIRED, "customerId=${command.customerId}")
}
if (billingQueryPort.hasOverdueInvoice(command.customerId)) {
    throw BusinessException(ErrorCode.OVERDUE_INVOICE_EXISTS, "customerId=${command.customerId}")
}
```

생성자에 `billingQueryPort: BillingQueryPort` 추가. `FakeBillingQueryPort`(토글 2개) + Contract 테스트는 기존 `FakeUserQueryPort` 패턴 복제. `OrderController.createOrder`의 Swagger 409 응답 설명에 두 코드 추가.

- [ ] **Step 5: 통과 확인 + Commit**

Run: `./gradlew :carry-order:test`
Expected: XML failures=0.

```bash
git add -A && git commit -m "feat: 주문 생성 전제조건 — 활성 빌링키 필수, 연체 고객 차단"
```

### Task 12: delivery 결제 게이트 제거

**Files:**
- Modify: `carry-delivery/src/main/kotlin/com/carry/delivery/application/service/DeliveryCommandService.kt`
- Delete: `carry-delivery/src/main/kotlin/com/carry/delivery/application/port/outbound/PaymentQueryPort.kt`
- Modify: `carry-delivery/src/main/kotlin/com/carry/delivery/domain/exception/DeliveryExceptions.kt` (OrderNotPaidException 삭제)
- Modify: `carry-common/src/main/kotlin/com/carry/common/exception/ErrorCode.kt` (ORDER_NOT_PAID 삭제)
- Delete: `carry-app/src/main/kotlin/com/carry/app/adapter/PaymentQueryPortAdapter.kt`
- Delete: `carry-delivery/src/testFixtures/kotlin/com/carry/delivery/application/port/outbound/contract/FakePaymentQueryPort.kt`, `PaymentQueryPortContract.kt`
- Delete: `carry-delivery/src/test/kotlin/com/carry/delivery/application/port/outbound/contract/FakePaymentQueryPortContractTest.kt`
- Delete: `carry-app/src/test/kotlin/com/carry/app/contract/PaymentQueryPortAdapterContractTest.kt`
- Modify: `carry-payment/src/main/kotlin/com/carry/payment/application/port/inbound/PaymentQueryUseCase.kt` + `PaymentQueryService.kt` (Task 8에서 Deprecated 한 isOrderPaid 실제 삭제, `PaymentQueryServiceTest` 해당 케이스 삭제)
- Test: `carry-delivery/src/test/kotlin/com/carry/delivery/application/service/DeliveryCommandServiceTest.kt`

- [ ] **Step 1: 실패하는 테스트 재작성**

`DeliveryCommandServiceTest.kt`의 CompleteDelivery 케이스에서 `FakePaymentQueryPort`·`markPaid` 셋업과 `결제되지 않은 주문의 배달 완료 시 예외` 케이스를 삭제하고, 핵심 검증을 교체:

```kotlin
@Test fun `결제 여부와 무관하게 배달을 완료할 수 있다 — 물리 흐름 무게이트 불변식`()
```

- [ ] **Step 2: 게이트 제거**

`completeDelivery`의 112–114행(`isOrderPaid` 가드) 삭제, 생성자에서 `paymentQueryPort` 제거, import 정리. 이어서 Files 목록의 삭제 대상 전부 삭제(`ORDER_NOT_PAID` enum 항목, `OrderNotPaidException`, delivery 포트, carry-app 어댑터, testFixtures, `PaymentQueryUseCase.isOrderPaid`).

- [ ] **Step 3: delivery·payment·app 컴파일+테스트 확인 + Commit**

Run: `./gradlew :carry-delivery:test :carry-payment:test :carry-app:compileTestKotlin`
Expected: XML failures=0. app 테스트 컴파일 통과로 어댑터·컨트랙트 테스트 삭제 완결 확인.

```bash
git add -A && git commit -m "refactor!: 배달 완료 결제 게이트 제거 — 물리 흐름은 결제를 기다리지 않는다"
```

### Task 13: carry-app 배선 + notification 보강

**Files:**
- Create: `carry-app/src/main/kotlin/com/carry/app/adapter/BillingQueryPortAdapter.kt`
- Modify: `carry-notification/src/main/kotlin/com/carry/notification/adapter/inbound/kafka/NotificationEventConsumer.kt`
- Modify: `carry-notification/src/main/kotlin/com/carry/notification/application/service/NotificationSagaHandler.kt`
- Modify: `carry-notification/src/main/kotlin/com/carry/notification/domain/vo/NotificationEnums.kt`
- Test: notification 단위 테스트 갱신

- [ ] **Step 1: BillingQueryPortAdapter**

```kotlin
@Component
class BillingQueryPortAdapter(
    private val billingQueryUseCase: BillingQueryUseCase,
) : BillingQueryPort {
    override fun hasActiveBillingKey(customerId: Long) = billingQueryUseCase.hasActiveBillingKey(customerId)
    override fun hasOverdueInvoice(customerId: Long) = billingQueryUseCase.hasOverdueInvoice(customerId)
}
```

- [ ] **Step 2: notification — RefundCompleted 소비 + 실패 안내 문구**

- `NotificationType`에 `REFUND_COMPLETED` 추가.
- `NotificationEventConsumer` 라우팅에 `"RefundCompletedEvent" -> handler.onRefundCompleted(...)` 추가.
- `NotificationSagaHandler.onRefundCompleted` 신설 (기존 `onPaymentCompleted` 구조 복제, 제목/내용은 환불 완료 안내).
- `onPaymentFailed`의 content 를 카드 재등록 유도 문구로 갱신: "결제 수단에 문제가 있어요. 카드를 다시 등록해 주세요. 세탁물은 정상적으로 배송됩니다."
- 각 핸들러 단위 테스트 케이스 추가/갱신.

- [ ] **Step 3: 전체 빌드 확인 + Commit**

Run: `./gradlew build -x test` 후 `./gradlew test`
Expected: 전 모듈 컴파일 + 모든 모듈 XML failures=0.

```bash
git add -A && git commit -m "feat: BillingQueryPort 배선 + 환불 완료 알림, 과금 실패 안내 문구"
```

### Task 14: 통합 테스트 재작성

**Files:**
- Modify: `carry-app/src/test/kotlin/com/carry/app/test/SagaIntegrationTestConfig.kt`, `FakePgProviderAdapter.kt` (Task 1에서 개편됨 — 확인만)
- Create: `carry-app/src/test/kotlin/com/carry/app/saga/AutoChargeSagaIntegrationTest.kt` (신규 — 구 `PaymentSagaIntegrationTest.kt`는 T9+T10에서 삭제됨, 파일 rewrite 아님)
- Modify: `carry-app/src/test/kotlin/com/carry/app/saga/OrderSagaIntegrationTest.kt`, `OrderCancellationSagaIntegrationTest.kt`, `SettlementLedgerIntegrationTest.kt`
- Modify: `carry-app/src/test/kotlin/com/carry/app/test/TestFixtures.kt` (빌링키 삽입 헬퍼 추가)

- [ ] **Step 1: TestFixtures 에 빌링키 픽스처 추가 + 컨버터 실 Hibernate 왕복 검증**

`insertBillingKey(customerId)` 헬퍼 — `BillingKeyService.register`를 fake PG로 호출(암호문이 컨버터 경유해야 하므로 서비스 호출 방식). 이 픽스처가 Testcontainers PostgreSQL로 저장→재조회를 실제로 태우므로, `BillingKeyCryptoConverter`가 Hibernate SpringBeanContainer로 부팅·주입되는지(단위 테스트가 우회한 경로)와 부분 유니크 인덱스 DDL이 함께 검증된다. 재등록 왕복(저장→invalidate→재등록→활성 키 1개 유지) 단언을 최소 1개 포함.

- [ ] **Step 2: 시나리오 ⓐ 해피 패스 (AutoChargeSagaIntegrationTest)**

주문 생성(빌링키 선등록) → 배차 → 수거(5.00kg) → **InvoiceIssuedEvent를 outbox에서 읽어 `PaymentSagaHandler.onInvoiceIssued` 직접 호출**(기존 통합 테스트의 hand-feed 패턴) → 검증: Payment COMPLETED·Invoice PAID·원장 4행 균형, 이후 세탁 시작→배달 완료로 **주문이 결제 이벤트 소비 없이 COMPLETED 도달**, outbox에 PaymentCompletedEvent 존재.

- [ ] **Step 3: 시나리오 ⓑ 과금 실패 경로**

`fakePg.shouldSucceed = false` → 수거 → 자동과금 실패 → 검증: Payment FAILED(+nextRetryAt), PaymentFailedEvent 1회, **주문은 계속 진행되어 COMPLETED 도달**. 이후 Clock 조작(기존 테스트의 Clock fixture 방식) 또는 인보이스 createdAt 백데이트로 OverdueSweeper 실행 → Invoice OVERDUE → **신규 주문 생성이 OVERDUE_INVOICE_EXISTS 로 409** → `fakePg.shouldSucceed = true` + 빌링키 재등록 → `ChargeRetrySweeper` 수동 트리거(nextRetryAt 백데이트) → Payment COMPLETED·Invoice PAID → 신규 주문 생성 성공.

- [ ] **Step 4: 시나리오 ⓒ 수거 후 취소**

과금 완료 후 코디 취소 → REFUND 원장 역분개·RefundCompletedEvent (기존 환불 케이스 이식). 과금 실패(FAILED) 상태에서 코디 취소 → Invoice CANCELLED + 스위퍼 재시도 대상 제외 검증.

- [ ] **Step 5: 기존 통합 테스트 갱신 (@Disabled 파킹 해제 포함)**

- `OrderSagaIntegrationTest`: 라이프사이클을 새 6-상태 기준으로 갱신.
- `SettlementLedgerIntegrationTest`: `progressToPaid()` → `progressToCharged()` — 수동 결제 호출 대신 `PaymentSagaHandler.onInvoiceIssued` 호출로 교체.
- `PaymentReconciliationIntegrationTest`: `progressToPaid()`를 자동과금 경로로 재작성 — CHARGE 레코드는 이제 `chargeBilling`이 남기므로 대사 시나리오 자체는 유지된다. REFUND_PENDING→REFUNDED 수렴 케이스는 결제 모듈 상태 검증으로 전환(주문 상태 아님).
- `OrderCancellationSagaIntegrationTest`: REFUND_PENDING 주문 상태 검증을 "주문 CANCELLED + payment 모듈 상태" 검증으로 교체.
- **빌링키 픽스처 전면 적용**: `createOrder`를 호출하는 모든 통합 테스트가 Task 11의 전제조건에 걸린다 — 위 4개 외에 `DispatchSagaIntegrationTest`, `OutboxAtomicityIntegrationTest`, `ConcurrencyIntegrationTest`, `QueryCountGuardTest`도 셋업에 `insertBillingKey(customerId)` 추가. T1·T9+T10에서 부착한 `@Disabled` 전부 제거. (T9+T10에서 `OrderSagaIntegrationTest`·`SettlementLedgerIntegrationTest`·`PaymentReconciliationIntegrationTest`는 컴파일 유지용으로 결제 호출부가 트리밍된 상태 — 여기서 자동과금 경로로 완성한다. `progressToPaid()` 오칭·discard된 outbox 읽기도 이때 정리.)

- [ ] **Step 6: 통합 테스트 실행 + Commit**

Run: `./gradlew :carry-app:test`
Expected: XML failures=0 (Testcontainers PostgreSQL 필요 — Docker 구동 확인).

```bash
git add -A && git commit -m "test: 자동과금 사가 통합 테스트 — 해피/실패·연체·회복/취소 3종 시나리오"
```

### Task 15: 문서·계약 갱신 + 최종 검증 + PR

**Files:**
- Modify: `docs/06-saga.md`
- Modify: `docs/14-client-retry-guide.md`
- Modify: `docs/operations/runbooks/saga-stuck.md`
- Modify: `carry-order/README.md` (상태 다이어그램)
- Modify: `infra/prometheus/rules/tests/carry-baseline.test.yml` (`status="INVOICED"` 샘플 라벨을 생존 상태로)
- Regenerate: `docs/api/openapi-v2.json`

- [ ] **Step 1: docs/06-saga.md 전면 갱신**

정상 흐름 다이어그램을 새 시나리오로 교체(주문 사가와 결제 사가를 별도 레인으로), 보상 섹션에서 "결제 실패→재결제→취소" 를 "과금 실패→백오프 재시도→연체(신규 주문 차단)→회복" 으로 교체, 상태 머신 다이어그램 갱신. 스펙 문서 링크 추가.

- [ ] **Step 2: 운영 문서 정리**

- `docs/14-client-retry-guide.md`: ORDER_NOT_PAID(402) 항목 제거, 신규 409 코드 2종(BILLING_KEY_REQUIRED, OVERDUE_INVOICE_EXISTS) 추가.
- `docs/operations/runbooks/saga-stuck.md`: WATCHED_STATUSES 목록·SQL 쿼리를 새 4개 감시 상태로, "재결제 시한 스위퍼 24h 종결" 가이드를 "과금 실패는 주문을 종결하지 않음 — ChargeRetrySweeper/OverdueSweeper 참조"로 교체.
- `carry-order/README.md` 상태 다이어그램을 6-상태로 재작성.
- prometheus 룰 테스트의 `status="INVOICED"` 샘플 라벨을 `PICKED_UP` 등 생존 상태로 교체.

- [ ] **Step 2.5: openapi 재생성**

기존 생성 절차(레포의 openapi 생성 태스크/스크립트 — 프론트 `#93` 타입 동기화에 쓰인 경로)를 확인해 `docs/api/openapi-v2.json` 재생성. 검증: `POST /pay` 부재, `/api/v2/billing-keys` 2종 존재, OrderResponse에서 totalAmount 부재, 주문 status enum 6개.

- [ ] **Step 3: 전체 빌드·테스트 최종 검증**

Run: `./gradlew clean build`
Expected: 전 모듈 컴파일 + 전 모듈 test-results XML에서 failures=0, errors=0. ArchUnit(HexagonalArchitectureTest) 포함 통과.

- [ ] **Step 4: Commit + PR**

```bash
git add -A && git commit -m "docs: 사가 문서 갱신 — 결제·물리 흐름 분리 반영"
git push -u origin feat/billing-key-autocharge
gh pr create --base develop --title "feat: 빌링키 자동과금 — 결제·물리 흐름 완전 분리" --body-file <PR본문파일>
```

PR 본문: 스펙 링크, 상태 기계 before/after, 시나리오 3종 테스트 근거, `Closes #<이슈번호>` (이슈가 없으면 생성 후 연결). 머지는 기존 관례대로 `gh pr merge --merge --delete-branch`.
