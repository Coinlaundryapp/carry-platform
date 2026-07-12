package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.BillingKey

interface BillingKeyPersistencePort {
    fun save(billingKey: BillingKey): BillingKey

    /** 고객의 현재 ACTIVE 빌링키(고객당 최대 1개 — DB 부분 유니크 인덱스로 보장). */
    fun findActiveByCustomerId(customerId: Long): BillingKey?

    fun existsActiveByCustomerId(customerId: Long): Boolean
}
