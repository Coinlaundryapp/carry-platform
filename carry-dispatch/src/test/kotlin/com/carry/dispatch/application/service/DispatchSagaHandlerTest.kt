package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.ShippingAddressDto
import com.carry.event.port.EventPublisherPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DispatchSagaHandlerTest {

    private val dispatchPersistencePort = mockk<DispatchPersistencePort>(relaxed = true)
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val sut = DispatchSagaHandler(dispatchPersistencePort, eventPublisher)

    private val now = Instant.now()

    @Test
    fun `OrderCreatedEvent 수신 시 PENDING 상태의 배차를 생성한다`() {
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
        val saved = slot<Dispatch>()
        every { dispatchPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onOrderCreated(event)

        assertThat(saved.captured.orderId).isEqualTo(1L)
        assertThat(saved.captured.laundromatId).isEqualTo(100L)
        assertThat(saved.captured.areaCode).isEqualTo("GANGNAM")
        assertThat(saved.captured.status).isEqualTo(DispatchStatus.PENDING)
    }

    @Test
    fun `OrderCancelledEvent 수신 시 배차를 취소하고 이벤트를 발행한다`() {
        val dispatch = Dispatch.reconstitute(
            id = 1L, orderId = 1L, laundromatId = 100L, status = DispatchStatus.PENDING,
            carrierId = null, areaCode = "GANGNAM",
            desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
            assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
            createdAt = now, updatedAt = now,
        )
        every { dispatchPersistencePort.findByOrderId(1L) } returns dispatch
        val saved = slot<Dispatch>()
        every { dispatchPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onOrderCancelled(OrderCancelledEvent(1L, "고객 변심", "CUSTOMER"))

        assertThat(saved.captured.status).isEqualTo(DispatchStatus.CANCELLED)
        assertThat(saved.captured.cancelReason).isEqualTo("고객 변심")
        verify { eventPublisher.publish("Dispatch", "1", "DispatchCancelledEvent", any(), any()) }
    }

    @Test
    fun `OrderCancelledEvent 수신 시 배차가 없으면 무시한다`() {
        every { dispatchPersistencePort.findByOrderId(1L) } returns null

        sut.onOrderCancelled(OrderCancelledEvent(1L, "고객 변심", "CUSTOMER"))

        verify(exactly = 0) { dispatchPersistencePort.save(any()) }
    }

    @Test
    fun `OrderCancelledEvent 수신 시 이미 취소된 배차는 무시한다`() {
        val dispatch = Dispatch.reconstitute(
            id = 1L, orderId = 1L, laundromatId = 100L, status = DispatchStatus.CANCELLED,
            carrierId = null, areaCode = "GANGNAM",
            desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
            assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = "이전 취소",
            createdAt = now, updatedAt = now,
        )
        every { dispatchPersistencePort.findByOrderId(1L) } returns dispatch

        sut.onOrderCancelled(OrderCancelledEvent(1L, "고객 변심", "CUSTOMER"))

        verify(exactly = 0) { dispatchPersistencePort.save(any()) }
    }
}
