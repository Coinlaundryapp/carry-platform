package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.Payment

interface PaymentQueryUseCase {
    fun getPayment(paymentId: Long): Payment
    fun getPaymentByOrder(orderId: Long, requestingUserId: Long): Payment

    @Deprecated("배달 완료 결제 게이트 폐기 — Task 12에서 delivery 포트와 함께 삭제")
    fun isOrderPaid(orderId: Long): Boolean
}
