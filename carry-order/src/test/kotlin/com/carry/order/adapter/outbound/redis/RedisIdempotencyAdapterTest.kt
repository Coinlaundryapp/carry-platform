package com.carry.order.adapter.outbound.redis

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class RedisIdempotencyAdapterTest {

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val redis = mockk<StringRedisTemplate>(relaxed = true)
    private val sut = RedisIdempotencyAdapter(redis, pendingTtl = Duration.ofSeconds(120), resultTtl = Duration.ofHours(24))

    init {
        every { redis.opsForValue() } returns valueOps
    }

    @Test
    fun `reserve 는 SETNX 성공 시 true 를 반환한다`() {
        every {
            valueOps.setIfAbsent("idem:order:create:k", "PENDING", Duration.ofSeconds(120))
        } returns true

        assertThat(sut.reserve("k")).isTrue()
    }

    @Test
    fun `reserve 는 키가 이미 있으면 false 를 반환한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false

        assertThat(sut.reserve("k")).isFalse()
    }

    @Test
    fun `findCompletedOrderId 는 저장된 숫자 문자열을 Long 으로 반환한다`() {
        every { valueOps.get("idem:order:create:k") } returns "42"

        assertThat(sut.findCompletedOrderId("k")).isEqualTo(42L)
    }

    @Test
    fun `findCompletedOrderId 는 PENDING(진행 중) 또는 부재면 null 이다`() {
        every { valueOps.get("idem:order:create:p") } returns "PENDING"
        every { valueOps.get("idem:order:create:absent") } returns null

        assertThat(sut.findCompletedOrderId("p")).isNull()
        assertThat(sut.findCompletedOrderId("absent")).isNull()
    }

    @Test
    fun `complete 는 결과 TTL 로 orderId 문자열을 저장한다`() {
        sut.complete("k", 42L)

        verify { valueOps.set("idem:order:create:k", "42", Duration.ofHours(24)) }
    }
}
