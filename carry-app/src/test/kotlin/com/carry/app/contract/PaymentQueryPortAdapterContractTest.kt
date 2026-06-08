package com.carry.app.contract

import com.carry.app.adapter.PaymentQueryPortAdapter
import com.carry.app.contract.fake.FakePaymentPersistencePort
import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.delivery.application.port.outbound.contract.PaymentQueryPortContract
import com.carry.payment.application.service.PaymentQueryService
import com.carry.payment.domain.vo.PaymentStatus

class PaymentQueryPortAdapterContractTest : PaymentQueryPortContract() {

    private val persistence = FakePaymentPersistencePort()
    private val adapter = PaymentQueryPortAdapter(PaymentQueryService(persistence))

    override fun subject(): PaymentQueryPort = adapter

    override fun arrangePaid(orderId: Long) {
        persistence.put(orderId, PaymentStatus.COMPLETED)
    }

    override fun arrangePendingPayment(orderId: Long) {
        persistence.put(orderId, PaymentStatus.PENDING)
    }

    override fun arrangeNoPayment(orderId: Long) {
        // put 하지 않음 → findByOrderId null → isOrderPaid false
    }
}
