package com.carry.order.domain.model

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
    )

    private val options = listOf(
        SelectedOption("WASH", "STANDARD"),
        SelectedOption("DRY", "LOW_HEAT"),
    )

    private val now = Instant.now()
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
    )

    private fun reconstitutedOrder(status: OrderStatus = OrderStatus.CREATED) = Order.reconstitute(
        id = 1L, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR", selectedOptions = options,
        shippingAddress = address, desiredPickupAt = pickupAt, desiredDeliveryAt = deliveryAt,
        carrierId = null, invoiceId = null, totalAmount = null, actualWeight = null,
        cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
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
        fun `옵션 없이 생성하면 예외가 발생한다`() {
            assertThatThrownBy {
                Order.create(1L, 10L, "REGULAR", emptyList(), address, pickupAt, deliveryAt)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("옵션")
        }

        @Test
        fun `배달 시각이 수거 시각 이전이면 예외가 발생한다`() {
            assertThatThrownBy {
                Order.create(1L, 10L, "REGULAR", options, address, deliveryAt, pickupAt)
            }.isInstanceOf(IllegalArgumentException::class.java)
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
        fun `청구서 발행 시 invoiceId와 totalAmount가 기록된다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            order.markInvoiced(200L, 15000L)
            assertThat(order.status).isEqualTo(OrderStatus.INVOICED)
            assertThat(order.invoiceId).isEqualTo(200L)
            assertThat(order.totalAmount).isEqualTo(15000L)
        }

        @Test
        fun `전체 Happy Path를 순차적으로 진행할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.markDispatched(100L)
            order.markPickedUp(BigDecimal("3.0"))
            order.markInvoiced(200L, 12000L)
            order.markPaid()
            order.markInProgress()
            order.markCompleted()
            assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
            assertThat(order.completedAt).isNotNull()
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
        fun `CREATED 상태에서 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.cancel("고객 요청", CancelledBy.CUSTOMER)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancelReason).isEqualTo("고객 요청")
            assertThat(order.cancelledBy).isEqualTo(CancelledBy.CUSTOMER)
            assertThat(order.cancelledAt).isNotNull()
        }

        @Test
        fun `DISPATCHED 상태에서 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.DISPATCHED)
            order.cancel("코디네이터 취소", CancelledBy.COORDINATOR)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        fun `PICKED_UP 이후에는 취소할 수 없다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            assertThatThrownBy { order.cancel("취소 시도", CancelledBy.CUSTOMER) }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }
    }
}
