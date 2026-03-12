package com.carry.order.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderSagaHandlerTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val sut = OrderSagaHandler(orderPersistencePort)

    private val now = Instant.now()
    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", null, 37.5, 127.0, "홍길동", "01012345678", null,
    )

    private fun orderAt(status: OrderStatus) = Order.reconstitute(
        id = 1L, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR", selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = if (status >= OrderStatus.DISPATCHED) 100L else null,
        invoiceId = null, totalAmount = null, actualWeight = null,
        cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `DispatchAcceptedEvent 수신 시 DISPATCHED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CREATED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchAccepted(DispatchAcceptedEvent(10L, 1L, 100L, 10L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.DISPATCHED)
        assertThat(saved.captured.carrierId).isEqualTo(100L)
    }

    @Test
    fun `DispatchTimeoutEvent 수신 시 CANCELLED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CREATED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchTimeout(DispatchTimeoutEvent(10L, 1L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(saved.captured.cancelledBy).isEqualTo(CancelledBy.SYSTEM)
    }

    @Test
    fun `PickupCompletedEvent 수신 시 PICKED_UP으로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.DISPATCHED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPickupCompleted(PickupCompletedEvent(
            10L, 1L, 100L, 1L, BigDecimal("5.0"), "REGULAR", "SOLO", "NEW",
            listOf(SelectedOptionSnapshot("WASH", "STANDARD")),
        ))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.PICKED_UP)
        assertThat(saved.captured.actualWeight).isEqualByComparingTo(BigDecimal("5.0"))
    }

    @Test
    fun `InvoiceIssuedEvent 수신 시 INVOICED로 전이한다`() {
        val order = orderAt(OrderStatus.PICKED_UP)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onInvoiceIssued(InvoiceIssuedEvent(200L, 1L, 15000L, emptyList()))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.INVOICED)
        assertThat(saved.captured.invoiceId).isEqualTo(200L)
        assertThat(saved.captured.totalAmount).isEqualTo(15000L)
    }

    @Test
    fun `PaymentCompletedEvent 수신 시 PAID로 전이한다`() {
        val order = orderAt(OrderStatus.INVOICED)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPaymentCompleted(PaymentCompletedEvent(300L, 1L, 200L, 15000L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun `DeliveryCompletedEvent 수신 시 COMPLETED로 전이한다`() {
        val order = orderAt(OrderStatus.IN_PROGRESS)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDeliveryCompleted(DeliveryCompletedEvent(10L, 1L, 100L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.COMPLETED)
    }
}
