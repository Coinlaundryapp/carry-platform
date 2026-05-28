package com.carry.app.metrics

import com.carry.infra.kafka.outbox.OutboxEventRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class OutboxMetricsScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val registry: MeterRegistry,
) {
    private val pendingCount = AtomicLong(0)

    @PostConstruct
    fun registerGauge() {
        Gauge.builder("carry.outbox.pending", pendingCount) { it.toDouble() }
            .description("Number of pending outbox events awaiting CDC pickup")
            .register(registry)
    }

    @Scheduled(fixedRate = 30_000)
    fun updatePendingCount() {
        pendingCount.set(outboxEventRepository.count())
    }
}
