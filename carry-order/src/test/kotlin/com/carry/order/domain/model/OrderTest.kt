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
        fun `CREATED 상태에서 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.CREATED)
            order.cancel("고객 요청", CancelledBy.CUSTOMER, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancelReason).isEqualTo("고객 요청")
            assertThat(order.cancelledBy).isEqualTo(CancelledBy.CUSTOMER)
            assertThat(order.cancelledAt).isEqualTo(now)
        }

        @Test
        fun `DISPATCHED 상태에서 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.DISPATCHED)
            order.cancel("코디네이터 취소", CancelledBy.COORDINATOR, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        fun `PICKED_UP 이후에는 취소할 수 없다`() {
            val order = reconstitutedOrder(OrderStatus.PICKED_UP)
            assertThatThrownBy { order.cancel("취소 시도", CancelledBy.CUSTOMER, now) }
                .isInstanceOf(OrderNotCancellableException::class.java)
        }

        @Test
        fun `PAYMENT_FAILED 상태에서 취소할 수 있다`() {
            val order = reconstitutedOrder(OrderStatus.PAYMENT_FAILED)
            order.cancel("재결제 시한 초과", CancelledBy.SYSTEM, now)
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancelledBy).isEqualTo(CancelledBy.SYSTEM)
        }
    }

    @Nested
    inner class Compensation {

        @Test
        fun `INVOICED에서 결제 실패 시 PAYMENT_FAILED로 전이한다`() {
            val order = reconstitutedOrder(OrderStatus.INVOICED)
            order.markPaymentFailed()
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }

        @Test
        fun `PAYMENT_FAILED가 아닌 상태에서 결제 실패 처리하면 예외가 발생한다`() {
            val order = reconstitutedOrder(OrderStatus.PAID)
            assertThatThrownBy { order.markPaymentFailed() }
                .isInstanceOf(InvalidOrderStatusTransitionException::class.java)
        }

        @Test
        fun `PAYMENT_FAILED에서 재결제 성공 시 PAID로 전이한다`() {
            val order = reconstitutedOrder(OrderStatus.PAYMENT_FAILED)
            order.markPaid()
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        fun `PAID에서 환불 보상 시작 시 REFUND_PENDING으로 전이한다`() {
            val order = reconstitutedOrder(OrderStatus.PAID)
            order.markRefundPending()
            assertThat(order.status).isEqualTo(OrderStatus.REFUND_PENDING)
        }

        @Test
        fun `REFUND_PENDING에서 환불 완료 시 REFUNDED로 전이한다`() {
            val order = reconstitutedOrder(OrderStatus.REFUND_PENDING)
            order.markRefunded()
            assertThat(order.status).isEqualTo(OrderStatus.REFUNDED)
        }

        @Test
        fun `PAID가 아닌 상태에서 환불 보상 시작하면 예외가 발생한다`() {
            val order = reconstitutedOrder(OrderStatus.INVOICED)
            assertThatThrownBy { order.markRefundPending() }
                .isInstanceOf(InvalidOrderStatusTransitionException::class.java)
        }
    }
}
