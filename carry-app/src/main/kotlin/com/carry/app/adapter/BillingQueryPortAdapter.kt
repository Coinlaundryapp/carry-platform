package com.carry.app.adapter

import com.carry.order.application.port.outbound.BillingQueryPort
import com.carry.payment.application.port.inbound.BillingQueryUseCase
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-order -> carry-payment
 *
 * Implements the BillingQueryPort defined in carry-order by delegating to carry-payment's BillingQueryUseCase.
 */
@Component
class BillingQueryPortAdapter(
    private val billingQueryUseCase: BillingQueryUseCase,
) : BillingQueryPort {

    override fun hasActiveBillingKey(customerId: Long): Boolean {
        return billingQueryUseCase.hasActiveBillingKey(customerId)
    }

    override fun hasOverdueInvoice(customerId: Long): Boolean {
        return billingQueryUseCase.hasOverdueInvoice(customerId)
    }
}
