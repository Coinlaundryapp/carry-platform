package com.carry.payment.application.service

import com.carry.event.order.OrderCancelledEvent
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class PaymentSagaHandlerTest {

    private val invoiceService = mockk<InvoiceService>(relaxed = true)
    private val paymentPersistencePort = mockk<PaymentPersistencePort>(relaxed = true)
    private val paymentCommandUseCase = mockk<PaymentCommandUseCase>(relaxed = true)

    private val sut = PaymentSagaHandler(invoiceService, paymentPersistencePort, paymentCommandUseCase)

    private val now = Instant.now()

    private fun aPayment(status: PaymentStatus) = Payment.reconstitute(
        id = 1L, invoiceId = 200L, orderId = 10L, customerId = 100L, status = status,
        pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = "tx_123", amount = 18000L,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

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
