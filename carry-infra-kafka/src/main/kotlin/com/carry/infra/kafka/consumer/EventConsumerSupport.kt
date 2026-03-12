package com.carry.infra.kafka.consumer

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventConsumerSupport(
    private val processedEventRepository: ProcessedEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processIfNotDuplicate(eventId: String, block: () -> Unit) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping duplicate event: {}", eventId)
            return
        }

        block()

        processedEventRepository.save(ProcessedEvent(id = eventId))
    }
}
