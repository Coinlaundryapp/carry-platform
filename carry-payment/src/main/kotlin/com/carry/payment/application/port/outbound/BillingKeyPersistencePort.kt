package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.BillingKey

interface BillingKeyPersistencePort {
    fun save(billingKey: BillingKey): BillingKey

    /**
     * 즉시 flush 하는 저장 — 부분 유니크 인덱스(customer_id) WHERE status='ACTIVE' 는 statement
     * 단위로 검사되는데, Hibernate 기본 flush 순서(INSERT→UPDATE)를 그대로 두면 재등록 시 새 ACTIVE
     * 행의 INSERT 가 기존 키 무효화 UPDATE 보다 먼저 나가 제약을 위반한다. 무효화 저장에 사용해
     * 새 키 저장 전에 확정시킨다.
     */
    fun saveAndFlush(billingKey: BillingKey): BillingKey

    /** 고객의 현재 ACTIVE 빌링키(고객당 최대 1개 — DB 부분 유니크 인덱스로 보장). */
    fun findActiveByCustomerId(customerId: Long): BillingKey?

    fun existsActiveByCustomerId(customerId: Long): Boolean
}
