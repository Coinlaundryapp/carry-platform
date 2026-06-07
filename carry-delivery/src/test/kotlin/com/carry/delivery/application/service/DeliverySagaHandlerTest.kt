package com.carry.delivery.application.service

import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.model.DeliveryStep
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeliverySagaHandlerTest {

    private val deliveryPersistencePort = mockk<DeliveryPersistencePort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = DeliverySagaHandler(deliveryPersistencePort, clock)

    private fun deliveryAt(status: DeliveryStatus): Delivery {
        val steps = DeliveryStepType.entries.map { stepType ->
            DeliveryStep.reconstitute(
                id = stepType.ordinal + 1L,
                deliveryId = 1L,
                stepType = stepType,
                status = StepStatus.PENDING,
                mediaIds = emptyList(),
                note = null,
                completedAt = null,
            )
        }
        return Delivery.reconstitute(
            id = 1L,
            orderId = 100L,
            dispatchId = 10L,
            carrierId = 50L,
            laundromatId = 200L,
            status = status,
            actualWeight = null,
            steps = steps,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `DispatchAcceptedEvent 수신 시 배달을 생성한다`() {
        val saved = slot<Delivery>()
        every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchAccepted(DispatchAcceptedEvent(10L, 100L, 50L, 200L))

        assertThat(saved.captured.orderId).isEqualTo(100L)
        assertThat(saved.captured.dispatchId).isEqualTo(10L)
        assertThat(saved.captured.carrierId).isEqualTo(50L)
        assertThat(saved.captured.laundromatId).isEqualTo(200L)
        assertThat(saved.captured.status).isEqualTo(DeliveryStatus.PICKUP_PENDING)
        assertThat(saved.captured.steps).hasSize(5)
    }

    @Test
    fun `OrderCancelledEvent 수신 시 배달을 취소한다`() {
        val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
        every { deliveryPersistencePort.findByOrderId(100L) } returns delivery
        val saved = slot<Delivery>()
        every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onOrderCancelled(OrderCancelledEvent(100L, "고객 변심", "CUSTOMER"))

        assertThat(saved.captured.status).isEqualTo(DeliveryStatus.CANCELLED)
    }

    @Test
    fun `OrderCancelledEvent 수신 시 배달이 없으면 무시한다`() {
        every { deliveryPersistencePort.findByOrderId(100L) } returns null

        sut.onOrderCancelled(OrderCancelledEvent(100L, "고객 변심", "CUSTOMER"))

        verify(exactly = 0) { deliveryPersistencePort.save(any()) }
    }
}
