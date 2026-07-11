package com.carry.order.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
import com.carry.common.metrics.MetricsPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class OrderSagaHandlerTest {

    private val orderPersistencePort = mockk<OrderPersistencePort>(relaxed = true)
    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val sut = OrderSagaHandler(orderPersistencePort, clock, metrics)
    private val address = OrderShippingAddress(
        "서울특별시 강남구 역삼로 1", "101호", null, 37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
    )

    private fun orderAt(status: OrderStatus) = Order.reconstitute(
        id = 1L, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR", selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address, desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = if (status >= OrderStatus.DISPATCHED) 100L else null,
        invoiceId = null, totalAmount = null, actualWeight = null,
        cancellation = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `DispatchAcceptedEvent 수신 시 DISPATCHED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CREATED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchAccepted(DispatchAcceptedEvent(10L, 1L, 100L, 10L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.DISPATCHED)
        assertThat(saved.captured.carrierId).isEqualTo(100L)
    }

    @Test
    fun `DispatchTimeoutEvent 수신 시 CANCELLED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CREATED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDispatchTimeout(DispatchTimeoutEvent(10L, 1L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(saved.captured.cancellation?.by).isEqualTo(CancelledBy.SYSTEM)
    }

    @Test
    fun `PickupCompletedEvent 수신 시 PICKED_UP으로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.DISPATCHED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPickupCompleted(PickupCompletedEvent(
            10L, 1L, 100L, 1L, BigDecimal("5.0"), "REGULAR", "SOLO", "NEW",
            listOf(SelectedOptionSnapshot("WASH", "STANDARD")),
        ))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.PICKED_UP)
        assertThat(saved.captured.actualWeight).isEqualByComparingTo(BigDecimal("5.0"))
    }

    @Test
    fun `InvoiceIssuedEvent 수신 시 INVOICED로 전이한다`() {
        val order = orderAt(OrderStatus.PICKED_UP)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onInvoiceIssued(InvoiceIssuedEvent(200L, 1L, 15000L, emptyList()))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.INVOICED)
        assertThat(saved.captured.invoiceId).isEqualTo(200L)
        assertThat(saved.captured.totalAmount).isEqualTo(15000L)
    }

    @Test
    fun `PaymentCompletedEvent 수신 시 PAID로 전이한다`() {
        val order = orderAt(OrderStatus.INVOICED)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPaymentCompleted(PaymentCompletedEvent(300L, 1L, 200L, 15000L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun `PaymentFailedEvent 수신 시 INVOICED에서 PAYMENT_FAILED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.INVOICED)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onPaymentFailed(PaymentFailedEvent(300L, 1L, "잔액 부족"))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }

    @Test
    fun `PaymentFailedEvent가 INVOICED가 아닌 주문에 도착하면 무시한다`() {
        // 이미 PAID 된 주문에 늦게 도착한 실패 이벤트 — 멱등/순서 안전
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.PAID)

        sut.onPaymentFailed(PaymentFailedEvent(300L, 1L, "잔액 부족"))

        verify(exactly = 0) { orderPersistencePort.save(any()) }
    }

    @Test
    fun `RefundCompletedEvent 수신 시 REFUND_PENDING에서 REFUNDED로 전이한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.REFUND_PENDING)
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onRefundCompleted(RefundCompletedEvent(300L, 1L, 19500L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.REFUNDED)
    }

    @Test
    fun `DeliveryCompletedEvent 수신 시 COMPLETED로 전이한다`() {
        val order = orderAt(OrderStatus.IN_PROGRESS)
        every { orderPersistencePort.findById(1L) } returns order
        val saved = slot<Order>()
        every { orderPersistencePort.save(capture(saved)) } answers { saved.captured }

        sut.onDeliveryCompleted(DeliveryCompletedEvent(10L, 1L, 100L))

        assertThat(saved.captured.status).isEqualTo(OrderStatus.COMPLETED)
    }

    @Test
    fun `취소된 주문에 늦게 도착한 forward 이벤트는 throw 없이 무시한다`() {
        // 취소 선커밋 vs 픽업 race — 늦은 forward 이벤트가 throw 되면 DLQ poison 이 된다.
        // reverse 핸들러(onPaymentFailed/onRefundCompleted)의 가드 패턴과 대칭으로 멱등 no-op.
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CANCELLED)

        sut.onDispatchAccepted(DispatchAcceptedEvent(10L, 1L, 100L, 10L))
        sut.onPickupCompleted(PickupCompletedEvent(
            10L, 1L, 100L, 1L, BigDecimal("5.0"), "REGULAR", "SOLO", "NEW",
            listOf(SelectedOptionSnapshot("WASH", "STANDARD")),
        ))
        sut.onInvoiceIssued(InvoiceIssuedEvent(200L, 1L, 15000L, emptyList()))
        sut.onPaymentCompleted(PaymentCompletedEvent(300L, 1L, 200L, 15000L))
        sut.onLaundryStarted(LaundryStartedEvent(10L, 1L))
        sut.onDeliveryCompleted(DeliveryCompletedEvent(10L, 1L, 100L))

        verify(exactly = 0) { orderPersistencePort.save(any()) }
    }

    @Test
    fun `환불 분기로 빠진 주문에 도착한 forward 이벤트는 무시한다`() {
        // PAID 취소 → REFUND_PENDING 인 주문에 늦은 LaundryStartedEvent — forward 재개 불가
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.REFUND_PENDING)

        sut.onLaundryStarted(LaundryStartedEvent(10L, 1L))

        verify(exactly = 0) { orderPersistencePort.save(any()) }
    }

    @Test
    fun `forward 이벤트 무시 시 skip 메트릭을 기록한다`() {
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.CANCELLED)

        sut.onLaundryStarted(LaundryStartedEvent(10L, 1L))

        verify {
            metrics.incrementCounter(
                "carry.saga.forward_skipped",
                "event" to "LaundryStartedEvent", "status" to "CANCELLED",
            )
        }
    }

    @Test
    fun `이른 forward 이벤트는 여전히 throw 하여 재시도로 치유한다`() {
        // cross-topic 순서 미보장: InvoiceIssuedEvent 가 PickupCompletedEvent 보다 먼저 소비될 수 있다.
        // 이 경우 no-op 하면 전이가 영구 유실되므로 throw → Kafka 재시도(1s×3)가 자가치유한다.
        every { orderPersistencePort.findById(1L) } returns orderAt(OrderStatus.DISPATCHED)

        org.assertj.core.api.Assertions.assertThatThrownBy {
            sut.onInvoiceIssued(InvoiceIssuedEvent(200L, 1L, 15000L, emptyList()))
        }.isInstanceOf(com.carry.order.domain.exception.InvalidOrderStatusTransitionException::class.java)
    }

    @Test
    fun `DeliveryCompletedEvent 수신 시 carry_saga_duration 을 주문 생성부터의 소요시간으로 기록한다`() {
        // 사가 시작(주문 생성) 3시간 전 → 완료 시각(고정 clock=now)까지 = 3시간.
        val createdAt = now.minus(3, ChronoUnit.HOURS)
        val order = Order.reconstitute(
            id = 1L, customerId = 1L, status = OrderStatus.IN_PROGRESS, laundromatId = 10L,
            laundryItemType = "REGULAR", selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
            shippingAddress = address, desiredPickupAt = createdAt, desiredDeliveryAt = now,
            carrierId = 100L, invoiceId = null, totalAmount = null, actualWeight = null,
            cancellation = null, completedAt = null,
            createdAt = createdAt, updatedAt = createdAt,
        )
        every { orderPersistencePort.findById(1L) } returns order
        every { orderPersistencePort.save(any()) } answers { firstArg() }

        sut.onDeliveryCompleted(DeliveryCompletedEvent(10L, 1L, 100L))

        verify { metrics.recordTimer("carry.saga.duration", Duration.ofHours(3)) }
    }
}
