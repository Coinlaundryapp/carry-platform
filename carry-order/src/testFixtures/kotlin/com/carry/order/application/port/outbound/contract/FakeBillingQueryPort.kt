package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.BillingQueryPort

class FakeBillingQueryPort : BillingQueryPort {
    private val activeBillingKeyByCustomer = mutableMapOf<Long, Boolean>()
    private val overdueInvoiceByCustomer = mutableMapOf<Long, Boolean>()

    /** 미설정 고객의 기본값 — 대다수 테스트가 "전제조건 통과"를 원하므로 true/false 로 기본 세팅. */
    var defaultHasActiveBillingKey: Boolean = true
    var defaultHasOverdueInvoice: Boolean = false

    fun setActiveBillingKey(customerId: Long, active: Boolean) {
        activeBillingKeyByCustomer[customerId] = active
    }

    fun setOverdueInvoice(customerId: Long, overdue: Boolean) {
        overdueInvoiceByCustomer[customerId] = overdue
    }

    override fun hasActiveBillingKey(customerId: Long): Boolean =
        activeBillingKeyByCustomer[customerId] ?: defaultHasActiveBillingKey

    override fun hasOverdueInvoice(customerId: Long): Boolean =
        overdueInvoiceByCustomer[customerId] ?: defaultHasOverdueInvoice
}
