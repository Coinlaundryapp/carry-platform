package com.carry.delivery.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.delivery.domain.exception.DeliveryNotOwnedException
import com.carry.delivery.domain.exception.OrderNotPaidException
import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.model.DeliveryStep
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.port.EventPublisherPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DeliveryCommandServiceTest {

    private val deliveryPersistencePort = mockk<DeliveryPersistencePort>(relaxed = true)
    private val paymentQueryPort = mockk<PaymentQueryPort>()
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = DeliveryCommandService(
        deliveryPersistencePort, paymentQueryPort, eventPublisher, metrics, clock,
    )

    private fun deliveryAt(status: DeliveryStatus, id: Long = 1L): Delivery {
        val steps = DeliveryStepType.entries.map { stepType ->
            DeliveryStep.reconstitute(
                id = stepType.ordinal + 1L,
                deliveryId = id,
                stepType = stepType,
                status = StepStatus.PENDING,
                mediaIds = emptyList(),
                note = null,
                completedAt = null,
            )
        }
        return Delivery.reconstitute(
            id = id,
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

    @Nested
    inner class CompletePickup {

        @Test
        fun `수거 완료 시 이벤트를 발행한다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
            every { deliveryPersistencePort.findById(1L) } returns delivery
            val saved = slot<Delivery>()
            every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.completePickup(
                deliveryId = 1L,
                weight = BigDecimal("5.0"),
                photoIds = listOf(1L, 2L),
                customerId = 99L,
                laundryItemType = "REGULAR",
                orderUnitType = "SOLO",
                orderRequestType = "NEW",
                selectedOptions = listOf(SelectedOptionSnapshot("WASH", "STANDARD")),
                requestingCarrierId = 50L,
            )

            assertThat(saved.captured.status).isEqualTo(DeliveryStatus.PICKED_UP)
            assertThat(saved.captured.actualWeight).isEqualByComparingTo(BigDecimal("5.0"))
            verify { eventPublisher.publish("Delivery", "1", "PickupCompletedEvent", any(), any()) }
        }
    }

    @Nested
    inner class StartWashing {

        @Test
        fun `세탁 시작 시 이벤트를 발행한다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKED_UP)
            every { deliveryPersistencePort.findById(1L) } returns delivery
            val saved = slot<Delivery>()
            every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.startWashing(1L, listOf(3L), 50L)

            assertThat(saved.captured.status).isEqualTo(DeliveryStatus.IN_LAUNDRY)
            verify { eventPublisher.publish("Delivery", "1", "LaundryStartedEvent", any(), any()) }
        }
    }

    @Nested
    inner class CompleteDrying {

        @Test
        fun `건조 완료 시 저장한다`() {
            val delivery = deliveryAt(DeliveryStatus.IN_LAUNDRY)
            every { deliveryPersistencePort.findById(1L) } returns delivery
            val saved = slot<Delivery>()
            every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.completeDrying(1L, listOf(4L), 50L)

            assertThat(saved.captured.status).isEqualTo(DeliveryStatus.LAUNDRY_COMPLETE)
        }
    }

    @Nested
    inner class CompleteDelivery {

        @Test
        fun `결제 완료된 주문의 배달을 완료하고 이벤트를 발행한다`() {
            val delivery = deliveryAt(DeliveryStatus.DELIVERY_PENDING)
            every { deliveryPersistencePort.findById(1L) } returns delivery
            every { paymentQueryPort.isOrderPaid(100L) } returns true
            val saved = slot<Delivery>()
            every { deliveryPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.completeDelivery(1L, listOf(5L), 50L)

            assertThat(saved.captured.status).isEqualTo(DeliveryStatus.DELIVERED)
            verify { eventPublisher.publish("Delivery", "1", "DeliveryCompletedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.delivery.completed") }
            verify { metrics.recordTimer("carry.delivery.duration", any<Duration>()) }
        }

        @Test
        fun `결제되지 않은 주문의 배달 완료 시 예외가 발생한다`() {
            val delivery = deliveryAt(DeliveryStatus.DELIVERY_PENDING)
            every { deliveryPersistencePort.findById(1L) } returns delivery
            every { paymentQueryPort.isOrderPaid(100L) } returns false

            assertThatThrownBy { sut.completeDelivery(1L, listOf(5L), 50L) }
                .isInstanceOf(OrderNotPaidException::class.java)
        }
    }

    @Nested
    inner class Ownership {

        @Test
        fun `배정받지 않은 캐리어가 상태를 변경하면 DeliveryNotOwnedException 이 발생한다`() {
            every { deliveryPersistencePort.findById(1L) } returns deliveryAt(DeliveryStatus.PICKED_UP) // carrierId=50L

            assertThatThrownBy { sut.startWashing(1L, listOf(3L), 999L) }
                .isInstanceOf(DeliveryNotOwnedException::class.java)
        }
    }
}
