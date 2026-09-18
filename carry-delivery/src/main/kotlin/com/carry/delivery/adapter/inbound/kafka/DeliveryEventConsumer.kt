package com.carry.delivery.adapter.inbound.kafka

import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DeliveryEventConsumer(
    private val sagaHandler: DeliverySagaEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["carry.Dispatch.events"], groupId = "carry-delivery-module")
    fun consumeDispatchEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-delivery-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "DispatchAcceptedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchAcceptedEvent::class.java)
                    sagaHandler.onDispatchAccepted(event)
                }
                "DispatchCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchCancelledEvent::class.java)
                    sagaHandler.onDispatchCancelled(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }

    @KafkaListener(topics = ["carry.Order.events"], groupId = "carry-delivery-module")
    fun consumeOrderEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-delivery-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "OrderCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCancelledEvent::class.java)
                    sagaHandler.onOrderCancelled(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
