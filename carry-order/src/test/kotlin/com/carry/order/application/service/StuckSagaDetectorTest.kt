package com.carry.order.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class StuckSagaDetectorTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val thresholdHours = 6L
    private val sut = StuckSagaDetector(orderPersistencePort, metrics, clock, thresholdHours)

    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", null, 37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )

    private fun orderAt(id: Long, status: OrderStatus) = Order.reconstitute(
        id = id, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR", selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = null, invoiceId = null, totalAmount = null, actualWeight = null,
        cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
        createdAt = now.minus(10, ChronoUnit.HOURS), updatedAt = now.minus(10, ChronoUnit.HOURS),
    )

    @Test
    fun `정체된 주문 감지 시 상태 태그로 carry_saga_stuck 을 증가시킨다`() {
        every { orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.DISPATCHED, any()) } returns
            listOf(orderAt(1L, OrderStatus.DISPATCHED))
        every { orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.INVOICED, any()) } returns
            listOf(orderAt(2L, OrderStatus.INVOICED))

        sut.detectStuckSagas()

        verify { metrics.incrementCounter("carry.saga.stuck", "status" to "DISPATCHED") }
        verify { metrics.incrementCounter("carry.saga.stuck", "status" to "INVOICED") }
    }

    @Test
    fun `정체된 주문이 없으면 메트릭을 발생시키지 않는다`() {
        // relaxed 목 기본값 = emptyList

        sut.detectStuckSagas()

        verify(exactly = 0) { metrics.incrementCounter(any(), *anyVararg()) }
    }

    @Test
    fun `cutoff 는 threshold 만큼 과거이고 종결·PAYMENT_FAILED 상태는 조회하지 않는다`() {
        sut.detectStuckSagas()

        val cutoff = now.minus(thresholdHours, ChronoUnit.HOURS)
        // 감시 대상은 cutoff 로 조회된다.
        verify { orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.CREATED, cutoff) }
        verify { orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.IN_PROGRESS, cutoff) }
        // 종결·전용 스위퍼 보유 상태는 감시 대상이 아니다(중복 경보 방지).
        listOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.PAYMENT_FAILED)
            .forEach { terminal ->
                verify(exactly = 0) { orderPersistencePort.findByStatusAndUpdatedAtBefore(terminal, any()) }
            }
    }
}
