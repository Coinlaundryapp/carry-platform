package com.carry.operation.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.ShippingAddressDto
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.operation.application.port.outbound.OperationEventPersistencePort
import com.carry.operation.domain.model.OperationEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class OperationSagaHandlerTest {

    private val operationEventPersistencePort = mockk<OperationEventPersistencePort>(relaxed = true)
    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sut = OperationSagaHandler(operationEventPersistencePort, clock)

    @Test
    fun `OrderCreatedEvent 수신 시 OperationEvent를 저장한다`() {
        val event = OrderCreatedEvent(
            orderId = 1L, customerId = 10L, laundromatId = 100L,
            laundryItemType = "REGULAR", selectedOptions = emptyList(),
            shippingAddress = ShippingAddressDto(
                "서울특별시 강남구 역삼로 1", "101호", 37.5, 127.0, "홍길동", "01012345678",
            ),
            desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
            desiredDeliveryAt = now.plus(6, ChronoUnit.HOURS),
            areaCode = "GANGNAM",
        )
        val saved = slot<OperationEvent>()
        every { operationEventPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onOrderCreated(event)

        assertThat(saved.captured.eventType).isEqualTo("OrderCreatedEvent")
        assertThat(saved.captured.aggregateType).isEqualTo("Order")
        assertThat(saved.captured.aggregateId).isEqualTo(1L)
        verify { operationEventPersistencePort.save(any()) }
    }

    @Test
    fun `OrderCancelledEvent 수신 시 OperationEvent를 저장한다`() {
        val event = OrderCancelledEvent(orderId = 1L, reason = "고객 변심", cancelledBy = "CUSTOMER")
        val saved = slot<OperationEvent>()
        every { operationEventPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onOrderCancelled(event)

        assertThat(saved.captured.eventType).isEqualTo("OrderCancelledEvent")
        assertThat(saved.captured.aggregateType).isEqualTo("Order")
        assertThat(saved.captured.aggregateId).isEqualTo(1L)
        assertThat(saved.captured.summary).contains("고객 변심")
    }

    @Test
    fun `DispatchAcceptedEvent 수신 시 OperationEvent를 저장한다`() {
        val event = DispatchAcceptedEvent(
            dispatchId = 1L, orderId = 10L, carrierId = 100L, laundromatId = 200L,
        )
        val saved = slot<OperationEvent>()
        every { operationEventPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchAccepted(event)

        assertThat(saved.captured.eventType).isEqualTo("DispatchAcceptedEvent")
        assertThat(saved.captured.aggregateType).isEqualTo("Dispatch")
        assertThat(saved.captured.aggregateId).isEqualTo(1L)
    }

    @Test
    fun `DeliveryCompletedEvent 수신 시 OperationEvent를 저장한다`() {
        val event = DeliveryCompletedEvent(
            deliveryId = 1L, orderId = 10L, carrierId = 100L,
        )
        val saved = slot<OperationEvent>()
        every { operationEventPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDeliveryCompleted(event)

        assertThat(saved.captured.eventType).isEqualTo("DeliveryCompletedEvent")
        assertThat(saved.captured.aggregateType).isEqualTo("Delivery")
        assertThat(saved.captured.aggregateId).isEqualTo(1L)
    }

    @Test
    fun `PaymentCompletedEvent 수신 시 OperationEvent를 저장한다`() {
        val event = PaymentCompletedEvent(
            paymentId = 1L, orderId = 10L, invoiceId = 5L, amount = 50000L,
        )
        val saved = slot<OperationEvent>()
        every { operationEventPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPaymentCompleted(event)

        assertThat(saved.captured.eventType).isEqualTo("PaymentCompletedEvent")
        assertThat(saved.captured.aggregateType).isEqualTo("Payment")
        assertThat(saved.captured.aggregateId).isEqualTo(1L)
        assertThat(saved.captured.summary).contains("50000")
    }
}
