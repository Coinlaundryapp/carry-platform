package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.BillingQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * BillingQueryPort 소비자(carry-order) 기대 계약.
 * - 소비자측: FakeBillingQueryPortContractTest (Fake가 계약 충실)
 * - provider측: BillingQueryPortAdapterContractTest (real 어댑터+carry-payment 가 계약 준수, carry-app 배선)
 */
abstract class BillingQueryPortContract {

    protected abstract fun subject(): BillingQueryPort

    /** customerId의 활성 빌링키 유무를 준비 */
    protected abstract fun arrangeActiveBillingKey(customerId: Long, active: Boolean)

    /** customerId의 연체 인보이스 유무를 준비 */
    protected abstract fun arrangeOverdueInvoice(customerId: Long, overdue: Boolean)

    @Test
    fun `활성 빌링키가 있으면 hasActiveBillingKey는 true`() {
        arrangeActiveBillingKey(1L, active = true)
        assertThat(subject().hasActiveBillingKey(1L)).isTrue()
    }

    @Test
    fun `활성 빌링키가 없으면 hasActiveBillingKey는 false`() {
        arrangeActiveBillingKey(2L, active = false)
        assertThat(subject().hasActiveBillingKey(2L)).isFalse()
    }

    @Test
    fun `연체 인보이스가 있으면 hasOverdueInvoice는 true`() {
        arrangeOverdueInvoice(3L, overdue = true)
        assertThat(subject().hasOverdueInvoice(3L)).isTrue()
    }

    @Test
    fun `연체 인보이스가 없으면 hasOverdueInvoice는 false`() {
        arrangeOverdueInvoice(4L, overdue = false)
        assertThat(subject().hasOverdueInvoice(4L)).isFalse()
    }
}
