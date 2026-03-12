package com.carry.payment.application.service

import com.carry.payment.application.port.inbound.PaymentQueryUseCase
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentQueryService(
    private val paymentPersistencePort: PaymentPersistencePort,
) : PaymentQueryUseCase {

    override fun getPayment(paymentId: Long): Payment {
        return paymentPersistencePort.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId.toString())
    }

    override fun getPaymentByOrder(orderId: Long): Payment {
        return paymentPersistencePort.findByOrderId(orderId)
            ?: throw PaymentNotFoundException("orderId=$orderId")
    }

    override fun isOrderPaid(orderId: Long): Boolean {
        val payment = paymentPersistencePort.findByOrderId(orderId)
        return payment != null && payment.status == PaymentStatus.COMPLETED
    }
}
