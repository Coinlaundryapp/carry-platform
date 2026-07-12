package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.BillingKey

interface BillingKeyUseCase {

    /**
     * 고객의 자동결제 수단을 등록한다. 이미 ACTIVE 빌링키가 있으면 기존 키를 무효화(INVALID)하고
     * 새 키를 ACTIVE 로 저장한다(고객당 활성 키 1개). customerKey 는 최초 등록 시 1회 생성되고
     * 이후 재등록에도 동일 값이 재사용된다(토스 권장 — PG 측 고객 식별자 안정성).
     * PG 가 발급을 거절하면 BILLING_KEY_ISSUE_FAILED 예외를 던진다.
     */
    fun register(customerId: Long, authKey: String): BillingKey

    /** 고객의 현재 ACTIVE 빌링키를 조회한다. 없으면 BILLING_KEY_NOT_FOUND 예외. */
    fun getActive(customerId: Long): BillingKey
}
