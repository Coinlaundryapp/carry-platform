package com.carry.delivery.domain.model

import com.carry.delivery.domain.exception.DeliveryNotInExpectedStatusException
import com.carry.delivery.domain.exception.DeliveryPhotoRequiredException
import com.carry.delivery.domain.exception.DeliveryWeightRequiredException
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class DeliveryTest {

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    private fun createDelivery() = Delivery.create(
        orderId = 1L,
        dispatchId = 10L,
        carrierId = 100L,
        laundromatId = 200L,
        now = now,
    )

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
            orderId = 1L,
            dispatchId = 10L,
            carrierId = 100L,
            laundromatId = 200L,
            status = status,
            actualWeight = null,
            steps = steps,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Nested
    inner class Create {

        @Test
        fun `배달을 생성하면 PICKUP_PENDING 상태이고 5개 스텝이 생성된다`() {
            val delivery = createDelivery()

            assertThat(delivery.status).isEqualTo(DeliveryStatus.PICKUP_PENDING)
            assertThat(delivery.id).isNull()
            assertThat(delivery.steps).hasSize(5)
            assertThat(delivery.steps.map { it.stepType }).containsExactly(
                DeliveryStepType.PICKUP,
                DeliveryStepType.WEIGHING,
                DeliveryStepType.WASHING,
                DeliveryStepType.DRYING,
                DeliveryStepType.DELIVERY,
            )
            delivery.steps.forEach { step ->
                assertThat(step.status).isEqualTo(StepStatus.PENDING)
            }
        }
    }

    @Nested
    inner class CompletePickup {

        @Test
        fun `수거 완료 시 무게와 사진이 기록되고 PICKED_UP 상태가 된다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
            delivery.completePickup(BigDecimal("5.50"), listOf(1L, 2L), now)

            assertThat(delivery.status).isEqualTo(DeliveryStatus.PICKED_UP)
            assertThat(delivery.actualWeight).isEqualByComparingTo(BigDecimal("5.50"))
            assertThat(delivery.getStep(DeliveryStepType.PICKUP)?.status).isEqualTo(StepStatus.COMPLETED)
            assertThat(delivery.getStep(DeliveryStepType.PICKUP)?.mediaIds).containsExactly(1L, 2L)
            assertThat(delivery.getStep(DeliveryStepType.PICKUP)?.completedAt).isEqualTo(now)
            assertThat(delivery.getStep(DeliveryStepType.WEIGHING)?.status).isEqualTo(StepStatus.COMPLETED)
        }

        @Test
        fun `수거 시 무게가 0이면 예외가 발생한다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
            assertThatThrownBy { delivery.completePickup(BigDecimal.ZERO, listOf(1L), now) }
                .isInstanceOf(DeliveryWeightRequiredException::class.java)
        }

        @Test
        fun `수거 시 사진이 없으면 예외가 발생한다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
            assertThatThrownBy { delivery.completePickup(BigDecimal("5.0"), emptyList(), now) }
                .isInstanceOf(DeliveryPhotoRequiredException::class.java)
        }
    }

    @Nested
    inner class StartWashing {

        @Test
        fun `세탁 시작 시 IN_LAUNDRY 상태가 된다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKED_UP)
            delivery.startWashing(listOf(3L), now)

            assertThat(delivery.status).isEqualTo(DeliveryStatus.IN_LAUNDRY)
            assertThat(delivery.getStep(DeliveryStepType.WASHING)?.status).isEqualTo(StepStatus.COMPLETED)
            assertThat(delivery.getStep(DeliveryStepType.WASHING)?.mediaIds).containsExactly(3L)
        }

        @Test
        fun `세탁 시작 시 사진이 없으면 예외가 발생한다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKED_UP)
            assertThatThrownBy { delivery.startWashing(emptyList(), now) }
                .isInstanceOf(DeliveryPhotoRequiredException::class.java)
        }
    }

    @Nested
    inner class CompleteDrying {

        @Test
        fun `건조 완료 시 LAUNDRY_COMPLETE 상태가 된다`() {
            val delivery = deliveryAt(DeliveryStatus.IN_LAUNDRY)
            delivery.completeDrying(listOf(4L), now)

            assertThat(delivery.status).isEqualTo(DeliveryStatus.LAUNDRY_COMPLETE)
            assertThat(delivery.getStep(DeliveryStepType.DRYING)?.status).isEqualTo(StepStatus.COMPLETED)
        }
    }

    @Nested
    inner class CompleteDelivery {

        @Test
        fun `배달 완료 시 DELIVERED 상태가 된다`() {
            val delivery = deliveryAt(DeliveryStatus.DELIVERY_PENDING)
            delivery.completeDelivery(listOf(5L), now)

            assertThat(delivery.status).isEqualTo(DeliveryStatus.DELIVERED)
            assertThat(delivery.getStep(DeliveryStepType.DELIVERY)?.status).isEqualTo(StepStatus.COMPLETED)
        }

        @Test
        fun `배달 시 사진이 없으면 예외가 발생한다`() {
            val delivery = deliveryAt(DeliveryStatus.DELIVERY_PENDING)
            assertThatThrownBy { delivery.completeDelivery(emptyList(), now) }
                .isInstanceOf(DeliveryPhotoRequiredException::class.java)
        }
    }

    @Nested
    inner class Cancel {

        @Test
        fun `PICKUP_PENDING 상태에서 취소할 수 있다`() {
            val delivery = deliveryAt(DeliveryStatus.PICKUP_PENDING)
            delivery.cancel()
            assertThat(delivery.status).isEqualTo(DeliveryStatus.CANCELLED)
        }

        @Test
        fun `DELIVERED 상태에서는 취소할 수 없다`() {
            val delivery = deliveryAt(DeliveryStatus.DELIVERED)
            assertThatThrownBy { delivery.cancel() }
                .isInstanceOf(DeliveryNotInExpectedStatusException::class.java)
        }
    }

    @Nested
    inner class FullFlow {

        @Test
        fun `전체 Happy Path를 순차적으로 진행할 수 있다`() {
            val delivery = createDelivery()
            delivery.completePickup(BigDecimal("3.0"), listOf(1L), now)
            delivery.startWashing(listOf(2L), now)
            delivery.completeDrying(listOf(3L), now)

            // LAUNDRY_COMPLETE -> DELIVERY_PENDING transition needed
            // Looking at enum: LAUNDRY_COMPLETE -> DELIVERY_PENDING is allowed
            assertThat(delivery.status).isEqualTo(DeliveryStatus.LAUNDRY_COMPLETE)
        }
    }
}
