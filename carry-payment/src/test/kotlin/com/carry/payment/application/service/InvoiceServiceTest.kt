package com.carry.payment.application.service

import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.exception.InvoiceNotOwnedException
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InvoiceServiceTest {

    private val invoicePersistencePort = mockk<InvoicePersistencePort>()
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = InvoiceService(invoicePersistencePort, eventPublisher, clock)

    private fun anInvoice(customerId: Long) = Invoice.reconstitute(
        id = 1L, orderId = 10L, customerId = customerId, status = InvoiceStatus.ISSUED,
        lineItems = listOf(InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용", 18000L)),
        weight = BigDecimal("3.0"), totalAmount = 18000L, createdAt = now, updatedAt = now,
    )

    @Test
    fun `소유자가 주문 청구서를 조회한다`() {
        every { invoicePersistencePort.findByOrderId(10L) } returns anInvoice(customerId = 100L)

        val result = sut.getInvoiceByOrder(10L, requestingUserId = 100L)

        assertThat(result.orderId).isEqualTo(10L)
    }

    @Test
    fun `다른 사용자가 조회하면 InvoiceNotOwnedException 이 발생한다`() {
        every { invoicePersistencePort.findByOrderId(10L) } returns anInvoice(customerId = 100L)

        assertThatThrownBy { sut.getInvoiceByOrder(10L, requestingUserId = 999L) }
            .isInstanceOf(InvoiceNotOwnedException::class.java)
    }
}
