package com.carry.infra.observability.metrics

import com.carry.common.metrics.MetricsPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * [MetricsPort]의 Micrometer 기반 구현체.
 *
 * 동일 이름·태그 조합으로 반복 호출돼도 [MeterRegistry]의 idempotent registration 보장에 따라
 * 같은 미터가 재사용된다. Timer만 캐싱하여 매번 객체 탐색 비용을 절감한다.
 */
@Component
class MicrometerMetricsAdapter(
    private val registry: MeterRegistry,
) : MetricsPort {

    private val timerCache = ConcurrentHashMap<String, Timer>()

    override fun incrementCounter(name: String, vararg tags: Pair<String, String>) {
        registry.counter(name, tags.toMicrometerTags()).increment()
    }

    override fun recordTimer(name: String, duration: Duration, vararg tags: Pair<String, String>) {
        val key = cacheKey(name, tags)
        timerCache.computeIfAbsent(key) {
            Timer.builder(name).tags(tags.toMicrometerTags()).register(registry)
        }.record(duration)
    }

    private fun Array<out Pair<String, String>>.toMicrometerTags(): List<Tag> =
        this.map { Tag.of(it.first, it.second) }

    private fun cacheKey(name: String, tags: Array<out Pair<String, String>>): String =
        if (tags.isEmpty()) name else name + tags.joinToString(prefix = "|") { "${it.first}=${it.second}" }
}
