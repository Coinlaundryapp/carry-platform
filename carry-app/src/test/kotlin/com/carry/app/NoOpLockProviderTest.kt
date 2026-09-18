package com.carry.app

import net.javacrumbs.shedlock.core.LockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class NoOpLockProviderTest {

    private val sut = NoOpLockProvider()

    @Test
    fun `항상 락을 획득하고 unlock 은 예외 없이 동작한다`() {
        val lock = sut.lock(
            LockConfiguration(Instant.now(), "any-lock", Duration.ofMinutes(1), Duration.ZERO),
        )

        assertThat(lock).isPresent
        assertThatCode { lock.get().unlock() }.doesNotThrowAnyException()
    }
}
