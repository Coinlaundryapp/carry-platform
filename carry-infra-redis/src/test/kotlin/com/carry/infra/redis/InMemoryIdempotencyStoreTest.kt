package com.carry.infra.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryIdempotencyStoreTest {

    private val sut = InMemoryIdempotencyStore()

    @Test
    fun `reserve 는 처음 1회만 true`() {
        assertThat(sut.reserve("k")).isTrue()
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test
    fun `complete 후 findCompletedId 가 결과를 반환한다`() {
        sut.reserve("k")
        sut.complete("k", 42L)
        assertThat(sut.findCompletedId("k")).isEqualTo(42L)
    }

    @Test
    fun `complete 된 키는 다시 reserve 되지 않는다`() {
        sut.reserve("k")
        sut.complete("k", 42L)
        assertThat(sut.reserve("k")).isFalse()
    }

    @Test
    fun `미처리 키의 findCompletedId 는 null`() {
        assertThat(sut.findCompletedId("absent")).isNull()
    }

    @Test
    fun `release 후 같은 키를 다시 reserve 할 수 있다`() {
        sut.reserve("k")
        sut.release("k")
        assertThat(sut.reserve("k")).isTrue()
    }

    @Test
    fun `release 는 complete 된 결과를 지우지 않는다`() {
        sut.reserve("k")
        sut.complete("k", 42L)
        sut.release("k")
        assertThat(sut.findCompletedId("k")).isEqualTo(42L)
    }
}
