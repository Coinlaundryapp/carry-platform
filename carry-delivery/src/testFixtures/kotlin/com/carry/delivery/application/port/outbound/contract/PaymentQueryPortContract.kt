package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class PaymentQueryPortContract {

    protected abstract fun subject(): PaymentQueryPort

    /** 주문이 결제완료(COMPLETED) 상태 */
    protected abstract fun arrangePaid(orderId: Long)
    /** 주문 결제가 존재하나 미완료(예: PENDING) */
    protected abstract fun arrangePendingPayment(orderId: Long)
    /** 주문에 결제 자체가 없음 */
    protected abstract fun arrangeNoPayment(orderId: Long)

    @Test
    fun `결제완료면 isOrderPaid는 true`() {
        arrangePaid(100L)
        assertThat(subject().isOrderPaid(100L)).isTrue()
    }

    @Test
    fun `결제가 미완료면 false`() {
        arrangePendingPayment(200L)
        assertThat(subject().isOrderPaid(200L)).isFalse()
    }

    @Test
    fun `결제가 없으면 false`() {
        arrangeNoPayment(300L)
        assertThat(subject().isOrderPaid(300L)).isFalse()
    }
}
