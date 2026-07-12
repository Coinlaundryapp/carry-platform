package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "payment_payments")
class PaymentJpaEntity(
    @Column(nullable = false)
    val invoiceId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val customerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val pgProvider: PgProvider,

    var pgTransactionId: String?,

    @Column(nullable = false)
    val amount: Long,

    var paidAt: Instant?,

    var failReason: String?,

    @Column(nullable = false)
    var retryCount: Int = 0,

    var nextRetryAt: Instant? = null,
) : BaseEntity() {

    /**
     * JPA optimistic locking 카운터. 두 트랜잭션이 동일 애그리거트를 동시 변경하면
     * 두 번째 commit에서 OptimisticLockingFailureException이 발생한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    fun toDomain(): Payment = Payment.reconstitute(
        id = id,
        invoiceId = invoiceId,
        orderId = orderId,
        customerId = customerId,
        status = status,
        pgProvider = pgProvider,
        pgTransactionId = pgTransactionId,
        amount = amount,
        paidAt = paidAt,
        failReason = failReason,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(payment: Payment) {
        status = payment.status
        pgTransactionId = payment.pgTransactionId
        paidAt = payment.paidAt
        failReason = payment.failReason
        retryCount = payment.retryCount
        nextRetryAt = payment.nextRetryAt
    }

    companion object {
        fun fromDomain(payment: Payment): PaymentJpaEntity = PaymentJpaEntity(
            invoiceId = payment.invoiceId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            status = payment.status,
            pgProvider = payment.pgProvider,
            pgTransactionId = payment.pgTransactionId,
            amount = payment.amount,
            paidAt = payment.paidAt,
            failReason = payment.failReason,
            retryCount = payment.retryCount,
            nextRetryAt = payment.nextRetryAt,
        )
    }
}
