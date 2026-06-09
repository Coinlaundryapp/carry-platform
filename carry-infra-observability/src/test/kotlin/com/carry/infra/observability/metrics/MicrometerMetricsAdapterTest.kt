package com.carry.infra.observability.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class MicrometerMetricsAdapterTest {

    private val registry = SimpleMeterRegistry()
    private val sut = MicrometerMetricsAdapter(registry)

    @Test
    fun `incrementCounter 는 카운터를 등록하고 증가시킨다`() {
        sut.incrementCounter("carry.test.counter")
        sut.incrementCounter("carry.test.counter")

        assertThat(registry.counter("carry.test.counter").count()).isEqualTo(2.0)
    }

    @Test
    fun `incrementCounter 는 태그를 적용한다`() {
        sut.incrementCounter("carry.test.tagged", "status" to "INVOICED")

        val counter = registry.find("carry.test.tagged").tag("status", "INVOICED").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `동일 name 과 tags 조합은 같은 미터로 재사용된다(중복 등록 없음)`() {
        sut.incrementCounter("carry.test.idem", "k" to "v")
        sut.incrementCounter("carry.test.idem", "k" to "v")

        // 같은 name+tags → 미터 1개, 누적 2.0
        val meters = registry.find("carry.test.idem").counters()
        assertThat(meters).hasSize(1)
        assertThat(meters.first().count()).isEqualTo(2.0)
    }

    @Test
    fun `서로 다른 태그 값은 별도 카운터로 집계된다`() {
        sut.incrementCounter("carry.test.split", "status" to "A")
        sut.incrementCounter("carry.test.split", "status" to "B")

        assertThat(registry.find("carry.test.split").counters()).hasSize(2)
        assertThat(registry.find("carry.test.split").tag("status", "A").counter()!!.count()).isEqualTo(1.0)
        assertThat(registry.find("carry.test.split").tag("status", "B").counter()!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `recordTimer 는 duration 을 기록한다`() {
        sut.recordTimer("carry.test.timer", Duration.ofSeconds(2))

        val timer = registry.find("carry.test.timer").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1L)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(2.0)
    }

    @Test
    fun `recordTimer 는 name 과 tags 별로 타이머를 분리하고 같은 키는 재사용한다`() {
        sut.recordTimer("carry.test.t", Duration.ofSeconds(1), "kind" to "x")
        sut.recordTimer("carry.test.t", Duration.ofSeconds(1), "kind" to "x") // 같은 키 → 재사용
        sut.recordTimer("carry.test.t", Duration.ofSeconds(1), "kind" to "y") // 다른 태그 → 분리

        assertThat(registry.find("carry.test.t").timers()).hasSize(2)
        assertThat(registry.find("carry.test.t").tag("kind", "x").timer()!!.count()).isEqualTo(2L)
        assertThat(registry.find("carry.test.t").tag("kind", "y").timer()!!.count()).isEqualTo(1L)
    }
}
