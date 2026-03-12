package com.carry.operation.adapter.inbound.kafka

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.carry.operation.application.port.inbound.OperationEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OperationEventConsumer(
    private val eventHandler: OperationEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["carry.Order.events", "carry.Dispatch.events", "carry.Delivery.events", "carry.Payment.events"],
        groupId = "carry-operation-module",
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id) {
            when (envelope.eventType) {
                "OrderCreatedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCreatedEvent::class.java)
                    eventHandler.onOrderCreated(event)
                }
                "OrderCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCancelledEvent::class.java)
                    eventHandler.onOrderCancelled(event)
                }
                "DispatchAcceptedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchAcceptedEvent::class.java)
                    eventHandler.onDispatchAccepted(event)
                }
                "DeliveryCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DeliveryCompletedEvent::class.java)
                    eventHandler.onDeliveryCompleted(event)
                }
                "PaymentCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PaymentCompletedEvent::class.java)
                    eventHandler.onPaymentCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
