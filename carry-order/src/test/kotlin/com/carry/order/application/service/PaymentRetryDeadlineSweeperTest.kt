package com.carry.order.application.service

import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
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

class PaymentRetryDeadlineSweeperTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val orderCommandUseCase = mockk<OrderCommandUseCase>(relaxed = true)

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = PaymentRetryDeadlineSweeper(orderPersistencePort, orderCommandUseCase, clock, deadlineHours = 24)
    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", "06230",
        37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )

    private fun paymentFailedOrder(id: Long) = Order.reconstitute(
        id = id, customerId = 1L, status = OrderStatus.PAYMENT_FAILED,
        laundromatId = 10L, laundryItemType = "REGULAR",
        selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = 100L, invoiceId = 200L, totalAmount = 18000L, actualWeight = java.math.BigDecimal("5.0"),
        cancellation = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `시한이 지난 PAYMENT_FAILED 주문을 SYSTEM으로 취소한다`() {
        val statusSlot = slot<OrderStatus>()
        val cutoffSlot = slot<Instant>()
        every {
            orderPersistencePort.findByStatusAndUpdatedAtBefore(capture(statusSlot), capture(cutoffSlot))
        } returns listOf(paymentFailedOrder(1L), paymentFailedOrder(2L))

        sut.sweepExpiredPaymentFailedOrders()

        assertThat(statusSlot.captured).isEqualTo(OrderStatus.PAYMENT_FAILED)
        // 고정 clock 기준 컷오프는 정확히 24시간 이전
        assertThat(cutoffSlot.captured).isEqualTo(now.minus(24, ChronoUnit.HOURS))
        verify { orderCommandUseCase.cancelOrder(1L, "재결제 시한 초과", "SYSTEM") }
        verify { orderCommandUseCase.cancelOrder(2L, "재결제 시한 초과", "SYSTEM") }
    }

    @Test
    fun `시한이 지난 주문이 없으면 아무 것도 취소하지 않는다`() {
        every { orderPersistencePort.findByStatusAndUpdatedAtBefore(any(), any()) } returns emptyList()

        sut.sweepExpiredPaymentFailedOrders()

        verify(exactly = 0) { orderCommandUseCase.cancelOrder(any(), any(), any()) }
    }

    @Test
    fun `한 주문 취소 실패가 나머지 주문 취소를 막지 않는다`() {
        // 멀티 인스턴스 레이스로 이미 취소된 주문은 예외가 날 수 있으나 배치는 계속되어야 한다
        every { orderPersistencePort.findByStatusAndUpdatedAtBefore(any(), any()) } returns
            listOf(paymentFailedOrder(1L), paymentFailedOrder(2L))
        every { orderCommandUseCase.cancelOrder(1L, any(), any()) } throws RuntimeException("이미 취소됨")

        sut.sweepExpiredPaymentFailedOrders()

        verify { orderCommandUseCase.cancelOrder(2L, "재결제 시한 초과", "SYSTEM") }
    }
}
