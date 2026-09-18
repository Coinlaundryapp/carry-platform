package com.carry.payment.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.payment.application.port.outbound.BillingKeyPersistencePort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgBillingKeyRequest
import com.carry.payment.application.port.outbound.PgBillingKeyResult
import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.vo.BillingKeyStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BillingKeyServiceTest {

    private val billingKeyPersistencePort = mockk<BillingKeyPersistencePort>()
    private val paymentGatewayResolver = mockk<PaymentGatewayResolver>()
    private val paymentGateway = mockk<PaymentGatewayPort>()
    private val auditPort = mockk<AuditPort>(relaxed = true)
    private val invoicePersistencePort = mockk<InvoicePersistencePort>()

    private val now = Instant.parse("2026-07-12T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = BillingKeyService(
        billingKeyPersistencePort, paymentGatewayResolver, auditPort, clock, invoicePersistencePort,
    )

    private val customerId = 100L
    private val authKey = "auth_test_key"

    private fun anActiveBillingKey(customerKey: String = "existing-customer-key") = BillingKey.reconstitute(
        id = 1L, customerId = customerId, customerKey = customerKey, billingKey = "old_billing_key",
        cardCompany = "국민", cardLast4 = "1111", status = BillingKeyStatus.ACTIVE,
        invalidatedAt = null, createdAt = now.minusSeconds(3600),
    )

    @Test
    fun `최초 등록 시 PG 발급 후 ACTIVE 로 저장한다`() {
        every { billingKeyPersistencePort.findActiveByCustomerId(customerId) } returns null
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        val issueReq = slot<PgBillingKeyRequest>()
        every { paymentGateway.issueBillingKey(capture(issueReq)) } returns PgBillingKeyResult(
            success = true, billingKey = "new_billing_key", cardCompany = "신한", cardLast4 = "2222",
        )
        val saved = slot<BillingKey>()
        every { billingKeyPersistencePort.save(capture(saved)) } answers { saved.captured }

        val result = sut.register(customerId, authKey)

        assertThat(result.status).isEqualTo(BillingKeyStatus.ACTIVE)
        assertThat(result.cardCompany).isEqualTo("신한")
        assertThat(result.cardLast4).isEqualTo("2222")
        assertThat(result.customerKey).isNotBlank()
        verify(exactly = 0) { billingKeyPersistencePort.saveAndFlush(any()) }
        // 최초 등록은 customerKey 를 재사용할 기존 키가 없으므로 새 UUID 가 생성돼 PG 로 전달돼야 한다.
        assertThat(issueReq.captured.customerKey).isNotBlank()
        assertThat(issueReq.captured.customerKey).isNotEqualTo("existing-customer-key")
        // UUID v4 형식(재사용이 아닌 신규 생성) 확인.
        assertThat(issueReq.captured.customerKey)
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        // 감사 페이로드는 등록 액션과 카드 마스킹 정보(cardLast4)를 담아야 한다.
        val auditAfter = slot<Any>()
        verify {
            auditPort.record(
                action = AuditAction.BILLING_KEY_REGISTERED,
                targetType = "BILLING_KEY",
                targetId = any(),
                before = any(),
                after = capture(auditAfter),
            )
        }
        @Suppress("UNCHECKED_CAST")
        val afterMap = auditAfter.captured as Map<String, Any?>
        assertThat(afterMap["cardLast4"]).isEqualTo("2222")
    }

    @Test
    fun `재등록 시 기존 ACTIVE 키를 INVALID 로 전환하고 새 키를 저장한다`() {
        val existing = anActiveBillingKey()
        every { billingKeyPersistencePort.findActiveByCustomerId(customerId) } returns existing
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.issueBillingKey(any()) } returns PgBillingKeyResult(
            success = true, billingKey = "new_billing_key", cardCompany = "신한", cardLast4 = "2222",
        )
        val invalidated = slot<BillingKey>()
        every { billingKeyPersistencePort.saveAndFlush(capture(invalidated)) } answers { invalidated.captured }
        val saved = slot<BillingKey>()
        every { billingKeyPersistencePort.save(capture(saved)) } answers { saved.captured }

        val result = sut.register(customerId, authKey)

        // 무효화(saveAndFlush)가 신규 저장(save)보다 먼저 확정돼야 부분 유니크 인덱스 위반을 피한다.
        verifyOrder {
            billingKeyPersistencePort.saveAndFlush(any())
            billingKeyPersistencePort.save(any())
        }
        assertThat(invalidated.captured.status).isEqualTo(BillingKeyStatus.INVALID)
        assertThat(result.status).isEqualTo(BillingKeyStatus.ACTIVE)
    }

    @Test
    fun `PG 발급 거절 시 BILLING_KEY_ISSUE_FAILED 예외`() {
        every { billingKeyPersistencePort.findActiveByCustomerId(customerId) } returns null
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.issueBillingKey(any()) } returns PgBillingKeyResult(
            success = false, failReason = "한도 초과",
        )

        assertThatThrownBy { sut.register(customerId, authKey) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex -> assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.BILLING_KEY_ISSUE_FAILED) })

        verify(exactly = 0) { billingKeyPersistencePort.save(any()) }
        verify(exactly = 0) { billingKeyPersistencePort.saveAndFlush(any()) }
    }

    @Test
    fun `customerKey 는 최초 등록 시 생성되고 재등록 시 재사용된다`() {
        val existing = anActiveBillingKey(customerKey = "cust-key-xyz")
        every { billingKeyPersistencePort.findActiveByCustomerId(customerId) } returns existing
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.issueBillingKey(any()) } returns PgBillingKeyResult(
            success = true, billingKey = "new_billing_key", cardCompany = "신한", cardLast4 = "2222",
        )
        every { billingKeyPersistencePort.saveAndFlush(any()) } answers { firstArg() }
        every { billingKeyPersistencePort.save(any()) } answers { firstArg() }

        sut.register(customerId, authKey)

        verify { paymentGateway.issueBillingKey(PgBillingKeyRequest(authKey, "cust-key-xyz")) }
    }

    @Test
    fun `활성 키 조회 - 없으면 BILLING_KEY_NOT_FOUND`() {
        every { billingKeyPersistencePort.findActiveByCustomerId(customerId) } returns null

        assertThatThrownBy { sut.getActive(customerId) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex -> assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.BILLING_KEY_NOT_FOUND) })
    }

    @Test
    fun `hasActiveBillingKey 는 billingKeyPersistencePort 의 existsActiveByCustomerId 에 위임한다`() {
        every { billingKeyPersistencePort.existsActiveByCustomerId(customerId) } returns true

        val result = sut.hasActiveBillingKey(customerId)

        assertThat(result).isTrue()
        verify { billingKeyPersistencePort.existsActiveByCustomerId(customerId) }
    }

    @Test
    fun `hasOverdueInvoice 는 invoicePersistencePort 의 existsOverdueByCustomerId 에 위임한다`() {
        every { invoicePersistencePort.existsOverdueByCustomerId(customerId) } returns true

        val result = sut.hasOverdueInvoice(customerId)

        assertThat(result).isTrue()
        verify { invoicePersistencePort.existsOverdueByCustomerId(customerId) }
    }
}
