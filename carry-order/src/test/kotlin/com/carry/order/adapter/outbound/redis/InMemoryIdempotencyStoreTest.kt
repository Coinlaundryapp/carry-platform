package com.carry.order.adapter.outbound.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryIdempotencyStoreTest {

    private val sut = InMemoryIdempotencyStore()

    @Test
    fun `같은 키는 한 번만 선점된다`() {
        assertThat(sut.reserve("k")).isTrue()
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test
    fun `complete 후 findCompletedOrderId 가 결과를 반환하고 재요청은 선점되지 않는다`() {
        sut.reserve("k")
        sut.complete("k", 42L)

        assertThat(sut.findCompletedOrderId("k")).isEqualTo(42L)
        assertThat(sut.reserve("k")).isFalse() // 이미 완료된 키
    }

    @Test
    fun `선점되지 않은 키의 결과 조회는 null 이다`() {
        assertThat(sut.findCompletedOrderId("unknown")).isNull()
    }
}
