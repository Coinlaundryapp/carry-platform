package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort

class FakePaymentQueryPortContractTest : PaymentQueryPortContract() {

    private val fake = FakePaymentQueryPort()

    override fun subject(): PaymentQueryPort = fake

    override fun arrangePaid(orderId: Long) {
        fake.markPaid(orderId)
    }

    override fun arrangePendingPayment(orderId: Long) {
        // markPaid 하지 않음 (Fake는 paid 여부만 추적; 미완료도 not-paid)
    }

    override fun arrangeNoPayment(orderId: Long) {
        // markPaid 하지 않음
    }
}
