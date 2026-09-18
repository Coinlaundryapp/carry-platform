package com.carry.order.domain.model

import com.carry.common.exception.BusinessException
import com.carry.order.domain.exception.InvalidOrderStatusTransitionException
import com.carry.order.domain.exception.OrderNotCancellableException
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderTest {

    private val address = OrderShippingAddress(
        roadAddress = "서울특별시 강남구 역삼로 1",
        detailAddress = "101호",
        zipCode = "06230",
        latitude = 37.5,
        longitude = 127.0,
        recipientName = "홍길동",
        recipientPhone = "01012345678",
        entranceInfo = null,
        areaCode = "GANGNAM",
    )

    private val options = listOf(
        SelectedOption("WASH", "STANDARD"),
        SelectedOption("DRY", "LOW_HEAT"),
    )

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val pickupAt = now.plus(2, ChronoUnit.HOURS)
    private val deliveryAt = now.plus(6, ChronoUnit.HOURS)

    private fun createOrder() = Order.create(
        customerId = 1L,
        laundromatId = 10L,
        laundryItemType = "REGULAR",
        selectedOptions = options,
        shippingAddress = address,
        desiredPickupAt = pickupAt,
        desiredDeliveryAt = deliveryAt,
        now = now,
    )

    private fun reconstitutedOrder(status: OrderStatus = OrderStatus.CREATED) = Order.reconstitute(
        id = 1L, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR", selectedOptions = options,
        shippingAddress = address, desiredPickupAt = pickupAt, desiredDeliveryAt = deliveryAt,
        carrierId = null, actualWeight = null,
        cancellation = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Nested
    inner class Create {

        @Test
        fun `주문을 생성하면 CREATED 상태이다`() {
            val order = createOrder()
            assertThat(order.status).isEqualTo(OrderStatus.CREATED)
            assertThat(order.id).isNull()
            assertThat(order.selectedOptions).hasSize(2)
        }

        @Test
        fun `생성 시 createdAt과 updatedAt은 주입된 now와 동일하다`() {
            val order = createOrder()
            assertThat(order.createdAt).isEqualTo(now)
            assertThat(order.updatedAt).isEqualTo(now)
        }

        @Test
        fun `옵션 없이 생성하면 예외가 발생한다`() {
            assertThatThrownBy {
                Order.create(1L, 10L, "REGULAR", emptyList(), address, pickupAt, deliveryAt, now)
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("옵션")
        }

        @Test
        fun `배달 시각이 수거 시각 이전이면 예외가 발생한다`() {
            assertThatThrownBy {
                Order.create(1L, 10L, "REGULAR", options, address, deliveryAt, pickupAt, now)
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("배달 희망 시각")
        }
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `배차 완료 시 carrierId가 기록된다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.markDispatched(100L)
            assertThat(order.status).isEqualTo(OrderStatus.DISPATCHED)
            assertThat(order.carrierId).isEqualTo(100L)
        }

        @Test
        fun `수거 완료 시 무게가 기록된다`() {
            val order = reconstitutedOrder(OrderStatus.DISPATCHED)
            order.markPickedUp(BigDecimal("5.50"))
            assertThat(order.status).isEqualTo(OrderStatus.PICKED_UP)
            assertThat(order.actualWeight).isEqualByComparingTo(BigDecimal("5.50"))
        }

        @Test
        fun `수거 완료 후 세탁 시작 시 IN_PROGRESS로 전이한다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            order.markInProgress()
            assertThat(order.status).isEqualTo(OrderStatus.IN_PROGRESS)
        }

        @Test
        fun `전체 Happy Path를 순차적으로 진행할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.markDispatched(100L)
            order.markPickedUp(BigDecimal("3.0"))
            order.markInProgress()
            order.markCompleted(now)
            assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
            assertThat(order.completedAt).isEqualTo(now)
        }

        @Test
        fun `유효하지 않은 상태 전이 시 예외가 발생한다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            assertThatThrownBy { order.markPickedUp(BigDecimal("3.0")) }
                .isInstanceOf(InvalidOrderStatusTransitionException::class.java)
        }
    }

    @Nested
    inner class Cancel {

        @Test
        fun `CREATED 상태에서 고객이 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.cancel("고객 요청", CancelledBy.CUSTOMER, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancellation?.reason).isEqualTo("고객 요청")
            assertThat(order.cancellation?.by).isEqualTo(CancelledBy.CUSTOMER)
            assertThat(order.cancellation?.at).isEqualTo(now)
        }

        @Test
        fun `DISPATCHED 상태에서 고객이 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.DISPATCHED)
            order.cancel("고객 요청", CancelledBy.CUSTOMER, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        fun `PICKED_UP 상태에서 고객이 취소하면 거부된다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            assertThatThrownBy { order.cancel("취소 시도", CancelledBy.CUSTOMER, now) }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }

        @Test
        fun `PICKED_UP 상태에서도 코디네이터는 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            order.cancel("세탁소 사정", CancelledBy.COORDINATOR, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancellation?.by).isEqualTo(CancelledBy.COORDINATOR)
        }

        @Test
        fun `IN_PROGRESS 상태에서도 시스템은 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.IN_PROGRESS)
            order.cancel("운영 사유", CancelledBy.SYSTEM, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancellation?.by).isEqualTo(CancelledBy.SYSTEM)
        }

        @Test
        fun `COMPLETED 상태에서는 누구도 취소할 수 없다`() {
            val order = reconstitutedOrder(OrderStatus.COMPLETED)
            assertThatThrownBy { order.cancel("취소 시도", CancelledBy.SYSTEM, now) }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }
    }
}
