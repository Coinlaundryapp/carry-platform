package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventConsumerSupport(
    private val processedEventRepository: ProcessedEventRepository,
    private val tracer: Tracer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping duplicate event: {}", eventId)
            return
        }

        val span = tracer.nextSpan().name("consume.${eventType ?: "unknown"}")
        traceId?.let { span.tag("saga.traceId", it) }
        span.start()
        try {
            tracer.withSpan(span).use {
                traceId?.let { MDC.put("saga.traceId", it) }
                block()
            }
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            MDC.remove("saga.traceId")
            span.end()
        }

        processedEventRepository.save(ProcessedEvent(id = eventId))
    }
}
