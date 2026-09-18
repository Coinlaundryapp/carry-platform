package com.carry.app

import io.mockk.mockk
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisConnectionFactory

class ShedLockConfigTest {

    private val sut = ShedLockConfig()

    @Test
    fun `RedisConnectionFactory 가 없으면 NoOp 락 프로바이더로 폴백한다`() {
        val provider = sut.lockProvider(null)

        assertThat(provider).isInstanceOf(NoOpLockProvider::class.java)
    }

    @Test
    fun `RedisConnectionFactory 가 있으면 Redis 락 프로바이더를 쓴다`() {
        val provider = sut.lockProvider(mockk<RedisConnectionFactory>(relaxed = true))

        assertThat(provider).isInstanceOf(RedisLockProvider::class.java)
    }
}
