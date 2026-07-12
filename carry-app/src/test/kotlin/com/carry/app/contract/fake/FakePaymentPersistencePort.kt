package com.carry.app.contract.fake

import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import java.time.Instant

class FakePaymentPersistencePort : PaymentPersistencePort {
    private val byOrderId = mutableMapOf<Long, Payment>()

    fun put(orderId: Long, status: PaymentStatus) {
        byOrderId[orderId] = Payment.reconstitute(
            id = orderId,
            invoiceId = orderId,
            orderId = orderId,
            customerId = 1L,
            status = status,
            pgProvider = PgProvider.TOSS_PAYMENTS,
            pgTransactionId = null,
            amount = 10_000L,
            paidAt = if (status == PaymentStatus.COMPLETED) Instant.EPOCH else null,
            failReason = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    override fun save(payment: Payment): Payment = payment
    override fun findById(id: Long): Payment? = byOrderId.values.find { it.id == id }
    override fun findByOrderId(orderId: Long): Payment? = byOrderId[orderId]
    override fun findByStatus(status: PaymentStatus): List<Payment> = byOrderId.values.filter { it.status == status }
    override fun findRetryableFailed(now: Instant): List<Payment> = byOrderId.values.filter {
        it.status == PaymentStatus.FAILED && it.nextRetryAt != null && !it.nextRetryAt!!.isAfter(now)
    }
    override fun findByPgTransactionId(pgTransactionId: String): Payment? =
        byOrderId.values.find { it.pgTransactionId == pgTransactionId }
    override fun findByProviderAndStatusInWindow(
        provider: PgProvider,
        statuses: Collection<PaymentStatus>,
        from: Instant,
        to: Instant,
    ): List<Payment> = byOrderId.values.filter {
        it.pgProvider == provider && it.status in statuses &&
            !it.updatedAt.isBefore(from) && !it.updatedAt.isAfter(to)
    }
}
