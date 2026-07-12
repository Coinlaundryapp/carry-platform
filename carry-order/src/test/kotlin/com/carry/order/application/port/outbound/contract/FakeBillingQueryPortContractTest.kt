package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.BillingQueryPort

class FakeBillingQueryPortContractTest : BillingQueryPortContract() {

    private val fake = FakeBillingQueryPort()

    override fun subject(): BillingQueryPort = fake

    override fun arrangeActiveBillingKey(customerId: Long, active: Boolean) {
        fake.setActiveBillingKey(customerId, active)
    }

    override fun arrangeOverdueInvoice(customerId: Long, overdue: Boolean) {
        fake.setOverdueInvoice(customerId, overdue)
    }
}
