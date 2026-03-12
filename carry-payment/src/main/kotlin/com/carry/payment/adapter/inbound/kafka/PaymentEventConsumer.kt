package com.carry.payment.adapter.inbound.kafka

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentEventConsumer(
    private val sagaHandler: PaymentSagaEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["carry.Delivery.events"], groupId = "carry-payment-module")
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "PickupCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PickupCompletedEvent::class.java)
                    sagaHandler.onPickupCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
