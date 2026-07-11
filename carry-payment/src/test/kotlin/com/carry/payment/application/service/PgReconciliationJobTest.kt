package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgTransactionRecord
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.application.port.outbound.ReconciliationMismatchPort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.model.ReconciliationMismatch
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import com.carry.payment.domain.vo.ReconciliationMismatchType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PgReconciliationJobTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>()
    private val resolver = mockk<PaymentGatewayResolver>()
    private val gateway = mockk<PaymentGatewayPort>()
    private val mismatchPort = mockk<ReconciliationMismatchPort>()
    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val now = Instant.parse("2026-07-12T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val lookbackMs = 90_000_000L // 25h
    private val graceMs = 600_000L // 10m
    private val from = now.minusMillis(lookbackMs)
    private val to = now.minusMillis(graceMs)

    private val sut = PgReconciliationJob(
        paymentPersistencePort, resolver, mismatchPort, metrics, clock, lookbackMs, graceMs,
    )

    @BeforeEach
    fun setUp() {
        every { resolver.supportedProviders() } returns setOf(PgProvider.TOSS_PAYMENTS)
        every { resolver.resolve(PgProvider.TOSS_PAYMENTS) } returns gateway
        every { mismatchPort.recordIfNew(any()) } returns true
        every { gateway.listTransactions(any(), any()) } returns emptyList()
        every { paymentPersistencePort.findByProviderAndStatusInWindow(any(), any(), any(), any()) } returns emptyList()
    }

    private fun aPayment(status: PaymentStatus, txId: String? = "tx-1", amount: Long = 18000L) = Payment.reconstitute(
        id = 1L, invoiceId = 200L, orderId = 10L, customerId = 100L, status = status,
        pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = txId, amount = amount,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

    private fun charge(txId: String = "tx-1", amount: Long = 18000L) =
        PgTransactionRecord(txId, PgTransactionType.CHARGE, amount, to.minusSeconds(3600))

    private fun cancel(txId: String = "tx-1", amount: Long = 18000L) =
        PgTransactionRecord(txId, PgTransactionType.CANCEL, amount, to.minusSeconds(1800))

    @Test
    fun `로컬과 PG 가 일치하면 아무것도 기록하지 않는다`() {
        val payment = aPayment(PaymentStatus.COMPLETED)
        every { gateway.listTransactions(from, to) } returns listOf(charge())
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns payment
        every {
            paymentPersistencePort.findByProviderAndStatusInWindow(PgProvider.TOSS_PAYMENTS, any(), any(), any())
        } returns listOf(payment)

        sut.reconcile()

        verify(exactly = 0) { mismatchPort.recordIfNew(any()) }
    }

    @Test
    fun `PG 과금인데 로컬 결제가 없으면 ORPHAN_PG_CHARGE 를 기록한다`() {
        every { gateway.listTransactions(from, to) } returns listOf(charge("tx-9"))
        every { paymentPersistencePort.findByPgTransactionId("tx-9") } returns null
        val recorded = slot<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        assertThat(recorded.captured.type).isEqualTo(ReconciliationMismatchType.ORPHAN_PG_CHARGE)
        assertThat(recorded.captured.dedupKey).isEqualTo("tx-9")
        assertThat(recorded.captured.pgAmount).isEqualTo(18000L)
        verify {
            metrics.incrementCounter(
                "carry.payment.reconcile.mismatch",
                "type" to "ORPHAN_PG_CHARGE", "provider" to "TOSS_PAYMENTS",
            )
        }
    }

    @Test
    fun `PG 과금인데 로컬 결제가 FAILED 상태면 ORPHAN_PG_CHARGE 를 기록한다`() {
        // 고객 돈은 나갔는데 로컬은 실패로 기록 — 가장 위험한 갭
        every { gateway.listTransactions(from, to) } returns listOf(charge())
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns aPayment(PaymentStatus.FAILED)
        val recorded = slot<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        assertThat(recorded.captured.type).isEqualTo(ReconciliationMismatchType.ORPHAN_PG_CHARGE)
        assertThat(recorded.captured.paymentId).isEqualTo(1L)
    }

    @Test
    fun `과금 금액이 다르면 AMOUNT_MISMATCH 를 기록한다`() {
        every { gateway.listTransactions(from, to) } returns listOf(charge(amount = 18000L))
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns
            aPayment(PaymentStatus.COMPLETED, amount = 17000L)
        val recorded = slot<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        assertThat(recorded.captured.type).isEqualTo(ReconciliationMismatchType.AMOUNT_MISMATCH)
        assertThat(recorded.captured.localAmount).isEqualTo(17000L)
        assertThat(recorded.captured.pgAmount).isEqualTo(18000L)
    }

    @Test
    fun `PG 취소 완료인데 로컬이 REFUND_PENDING 이면 PG_CANCEL_NOT_MARKED 를 기록한다`() {
        // executeRefund 가 PG 성공 후 로컬 마킹 직전 실패한 윈도 — P2b 수렴 대상
        val payment = aPayment(PaymentStatus.REFUND_PENDING)
        every { gateway.listTransactions(from, to) } returns listOf(charge(), cancel())
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns payment
        val recorded = mutableListOf<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        // charge 는 REFUND_PENDING(완료 이력 있음)이라 정상, cancel 만 불일치
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().type).isEqualTo(ReconciliationMismatchType.PG_CANCEL_NOT_MARKED)
        assertThat(recorded.single().detail).contains("P2b")
    }

    @Test
    fun `로컬 COMPLETED 인데 PG 과금 기록이 없으면 MISSING_IN_PG 를 기록한다`() {
        val payment = aPayment(PaymentStatus.COMPLETED)
        every {
            paymentPersistencePort.findByProviderAndStatusInWindow(PgProvider.TOSS_PAYMENTS, any(), any(), any())
        } returns listOf(payment)
        val recorded = slot<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        assertThat(recorded.captured.type).isEqualTo(ReconciliationMismatchType.MISSING_IN_PG)
        assertThat(recorded.captured.orderId).isEqualTo(10L)
    }

    @Test
    fun `로컬 REFUNDED 인데 PG 취소 기록이 없으면 MISSING_PG_CANCEL 를 기록한다`() {
        val payment = aPayment(PaymentStatus.REFUNDED)
        every { gateway.listTransactions(from, to) } returns listOf(charge())
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns payment
        every {
            paymentPersistencePort.findByProviderAndStatusInWindow(PgProvider.TOSS_PAYMENTS, any(), any(), any())
        } returns listOf(payment)
        val recorded = slot<ReconciliationMismatch>()
        every { mismatchPort.recordIfNew(capture(recorded)) } returns true

        sut.reconcile()

        assertThat(recorded.captured.type).isEqualTo(ReconciliationMismatchType.MISSING_PG_CANCEL)
    }

    @Test
    fun `이미 원장에 있는 불일치는 메트릭을 증가시키지 않는다`() {
        // 윈도 중첩 재스캔 — recordIfNew=false 면 중복 알럿 억제
        every { gateway.listTransactions(from, to) } returns listOf(charge("tx-9"))
        every { paymentPersistencePort.findByPgTransactionId("tx-9") } returns null
        every { mismatchPort.recordIfNew(any()) } returns false

        sut.reconcile()

        verify(exactly = 0) { metrics.incrementCounter("carry.payment.reconcile.mismatch", *anyVararg()) }
    }

    @Test
    fun `PG 목록 조회가 실패해도 잡은 죽지 않고 해당 provider 만 skip 한다`() {
        every { gateway.listTransactions(any(), any()) } throws RuntimeException("PG down")

        sut.reconcile()

        verify { metrics.incrementCounter("carry.payment.reconcile.pull_failed", "provider" to "TOSS_PAYMENTS") }
        verify(exactly = 0) { paymentPersistencePort.findByProviderAndStatusInWindow(any(), any(), any(), any()) }
        verify(exactly = 0) { mismatchPort.recordIfNew(any()) }
    }

    @Test
    fun `PG 윈도는 grace 만큼 뒤로, 로컬 윈도는 추가로 grace 만큼 안쪽으로 좁힌다`() {
        val payment = aPayment(PaymentStatus.COMPLETED)
        every { gateway.listTransactions(from, to) } returns listOf(charge())
        every { paymentPersistencePort.findByPgTransactionId("tx-1") } returns payment

        sut.reconcile()

        verify { gateway.listTransactions(from, to) }
        verify {
            paymentPersistencePort.findByProviderAndStatusInWindow(
                PgProvider.TOSS_PAYMENTS,
                listOf(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED),
                from.plusMillis(graceMs),
                to,
            )
        }
    }
}
