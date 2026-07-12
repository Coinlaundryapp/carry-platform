package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OverdueSweeperTest {

    private val invoicePersistencePort = mockk<InvoicePersistencePort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val now = Instant.parse("2026-06-10T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val thresholdHours = 72L
    private val cutoff = now.minus(Duration.ofHours(thresholdHours))

    private val sut = OverdueSweeper(invoicePersistencePort, metrics, clock, thresholdHours)

    private fun issuedInvoice(id: Long) = Invoice.reconstitute(
        id = id, orderId = id, customerId = 100L, status = InvoiceStatus.ISSUED,
        lineItems = listOf(InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁비", 18000L)),
        weight = BigDecimal("3.0"), totalAmount = 18000L,
        createdAt = now.minus(Duration.ofHours(73)), updatedAt = now.minus(Duration.ofHours(73)),
    )

    @Test
    fun `발행 72h 경과 ISSUED 인보이스를 OVERDUE 로 마킹한다`() {
        every { invoicePersistencePort.findIssuedBefore(cutoff) } returns listOf(issuedInvoice(1L))
        every { invoicePersistencePort.markOverdueIfIssued(1L, now) } returns true

        sut.markOverdueInvoices()

        verify { invoicePersistencePort.markOverdueIfIssued(1L, now) }
        verify { metrics.incrementCounter("carry.payment.invoice_overdue") }
    }

    @Test
    fun `72h 미만은 건드리지 않는다`() {
        every { invoicePersistencePort.findIssuedBefore(cutoff) } returns emptyList()

        sut.markOverdueInvoices()

        verify(exactly = 0) { invoicePersistencePort.markOverdueIfIssued(any(), any()) }
        verify(exactly = 0) { metrics.incrementCounter("carry.payment.invoice_overdue") }
    }

    @Test
    fun `조회 후 PAID 로 바뀐 인보이스는 OVERDUE 로 덮어쓰지 않는다`() {
        every { invoicePersistencePort.findIssuedBefore(cutoff) } returns listOf(issuedInvoice(1L))
        every { invoicePersistencePort.markOverdueIfIssued(1L, now) } returns false

        sut.markOverdueInvoices()

        verify { invoicePersistencePort.markOverdueIfIssued(1L, now) }
        verify(exactly = 0) { metrics.incrementCounter("carry.payment.invoice_overdue") }
    }
}
