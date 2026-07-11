package com.carry.payment.application.service

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class PaymentSagaHandlerTest {

    private val invoiceService = mockk<InvoiceService>(relaxed = true)
    private val paymentPersistencePort = mockk<PaymentPersistencePort>(relaxed = true)
    private val paymentCommandUseCase = mockk<PaymentCommandUseCase>(relaxed = true)
    private val orderStateQueryPort = mockk<OrderStateQueryPort>()

    private val sut = PaymentSagaHandler(invoiceService, paymentPersistencePort, paymentCommandUseCase, orderStateQueryPort)

    private val now = Instant.now()

    private fun aPickupEvent() = PickupCompletedEvent(
        deliveryId = 1L, orderId = 10L, carrierId = 100L, customerId = 100L,
        actualWeight = BigDecimal("3.00"), laundryItemType = "NORMAL",
        orderUnitType = "KG", orderRequestType = "STANDARD", selectedOptions = emptyList(),
    )

    private fun aPayment(status: PaymentStatus) = Payment.reconstitute(
        id = 1L, invoiceId = 200L, orderId = 10L, customerId = 100L, status = status,
        pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = "tx_123", amount = 18000L,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

    @Test
    fun `수거 완료 시 인보이스 발행 가능한 주문이면 인보이스를 발행한다`() {
        every { orderStateQueryPort.isInvoiceable(10L) } returns true

        sut.onPickupCompleted(aPickupEvent())

        verify { invoiceService.createInvoiceFromPickup(any()) }
    }

    @Test
    fun `수거 완료 시 취소·종결된 주문이면 인보이스를 발행하지 않는다`() {
        // 취소 선커밋 vs 픽업 race — 취소된 주문에 유령 인보이스가 발행되는 것을 차단
        every { orderStateQueryPort.isInvoiceable(10L) } returns false

        sut.onPickupCompleted(aPickupEvent())

        verify(exactly = 0) { invoiceService.createInvoiceFromPickup(any()) }
    }

    @Test
    fun `주문 취소 시 완료된 결제가 있으면 환불 대기로 표시한다`() {
        every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.COMPLETED)

        sut.onOrderCancelled(OrderCancelledEvent(10L, "세탁소 사정", "COORDINATOR"))

        // PG 즉시 호출이 아니라 환불 대기 표시 — 실제 PG 환불은 RefundRetrySweeper 가 수행(DLQ 위험 제거).
        verify { paymentCommandUseCase.markRefundPending(10L) }
    }

    @Test
    fun `주문 취소 시 결제가 없으면 환불 대기 표시를 하지 않는다`() {
        // 선결제 없는 주문(CREATED/DISPATCHED 단계) 취소 — throw 하면 DLQ 로 빠지므로 조용히 skip
        every { paymentPersistencePort.findByOrderId(10L) } returns null

        sut.onOrderCancelled(OrderCancelledEvent(10L, "고객 변심", "CUSTOMER"))

        verify(exactly = 0) { paymentCommandUseCase.markRefundPending(any()) }
    }

    @Test
    fun `주문 취소 시 결제가 완료 상태가 아니면 환불 대기 표시를 하지 않는다`() {
        // 결제 실패(FAILED) 후 시한 초과로 취소된 경우 — 환불할 결제가 없음
        every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.FAILED)

        sut.onOrderCancelled(OrderCancelledEvent(10L, "재결제 시한 초과", "SYSTEM"))

        verify(exactly = 0) { paymentCommandUseCase.markRefundPending(any()) }
    }
}
