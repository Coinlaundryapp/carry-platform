package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * LaundromatQueryPort 소비자(carry-order) 기대 계약.
 * - 소비자측: FakeLaundromatQueryPortContractTest (Fake가 계약 충실)
 * - provider측: LaundromatQueryPortAdapterContractTest (real 어댑터+서비스가 계약 준수)
 */
abstract class LaundromatQueryPortContract {

    protected abstract fun subject(): LaundromatQueryPort

    /** laundromatId가 존재하는 상태로 준비 */
    protected abstract fun arrangeExisting(laundromatId: Long)

    /** laundromatId가 존재하지 않는 상태로 준비 */
    protected abstract fun arrangeMissing(laundromatId: Long)

    @Test
    fun `존재하는 세탁소면 existsById는 true`() {
        arrangeExisting(100L)
        assertThat(subject().existsById(100L)).isTrue()
    }

    @Test
    fun `존재하지 않는 세탁소면 existsById는 false (provider의 NotFound 예외를 false로 변환)`() {
        arrangeMissing(999L)
        assertThat(subject().existsById(999L)).isFalse()
    }
}
