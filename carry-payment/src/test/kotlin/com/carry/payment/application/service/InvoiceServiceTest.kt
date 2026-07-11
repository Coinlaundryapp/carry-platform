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

    private fun aPickupEvent(weight: String) = com.carry.event.delivery.PickupCompletedEvent(
        deliveryId = 1L, orderId = 10L, carrierId = 100L, customerId = 100L,
        actualWeight = BigDecimal(weight), laundryItemType = "NORMAL",
        orderUnitType = "KG", orderRequestType = "STANDARD", selectedOptions = emptyList(),
    )

    private fun stubSaveEcho() {
        every { invoicePersistencePort.save(any()) } answers {
            val inv = firstArg<Invoice>()
            Invoice.reconstitute(
                id = 1L, orderId = inv.orderId, customerId = inv.customerId, status = inv.status,
                lineItems = inv.lineItems, weight = inv.weight, totalAmount = inv.totalAmount,
                createdAt = now, updatedAt = now,
            )
        }
    }

    @Test
    fun `수거 완료로 인보이스를 발행하면 세탁비의 10퍼센트가 서비스 수수료다`() {
        stubSaveEcho()

        val invoice = sut.createInvoiceFromPickup(aPickupEvent("2.50"))

        val fee = invoice.lineItems.first { it.chargeType == ChargeType.SERVICE_FEE }
        assertThat(fee.amount).isEqualTo(750L) // 2.5kg × 3000 = 7500 → 10% = 750
    }

    @Test
    fun `서비스 수수료는 HALF_UP 으로 반올림한다`() {
        // 돈 경로에 Double(IEEE-754) 곱을 쓰면 700.4999… 절삭으로 원 단위가 새는 케이스:
        // 2.335kg × 3000 = 7005원 → 10% = 700.5 → HALF_UP 701
        stubSaveEcho()

        val invoice = sut.createInvoiceFromPickup(aPickupEvent("2.335"))

        val fee = invoice.lineItems.first { it.chargeType == ChargeType.SERVICE_FEE }
        assertThat(fee.amount).isEqualTo(701L)
    }

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
