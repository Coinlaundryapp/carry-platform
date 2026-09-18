package com.carry.dispatch.adapter.inbound.kafka

import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DispatchEventConsumer(
    private val sagaHandler: DispatchSagaEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["carry.Order.events"], groupId = "carry-dispatch-module")
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-dispatch-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "OrderCreatedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCreatedEvent::class.java)
                    sagaHandler.onOrderCreated(event)
                }
                "OrderCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCancelledEvent::class.java)
                    sagaHandler.onOrderCancelled(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
