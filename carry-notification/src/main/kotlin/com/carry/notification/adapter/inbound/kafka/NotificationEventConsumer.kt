package com.carry.notification.adapter.inbound.kafka

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.carry.notification.application.port.inbound.NotificationEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationEventConsumer(
    private val eventHandler: NotificationEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["carry.Order.events", "carry.Dispatch.events", "carry.Delivery.events", "carry.Payment.events"],
        groupId = "carry-notification-module",
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "OrderCreatedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCreatedEvent::class.java)
                    eventHandler.onOrderCreated(event)
                }
                "DispatchAcceptedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchAcceptedEvent::class.java)
                    eventHandler.onDispatchAccepted(event)
                }
                "PickupCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PickupCompletedEvent::class.java)
                    eventHandler.onPickupCompleted(event)
                }
                "InvoiceIssuedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, InvoiceIssuedEvent::class.java)
                    eventHandler.onInvoiceIssued(event)
                }
                "PaymentCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PaymentCompletedEvent::class.java)
                    eventHandler.onPaymentCompleted(event)
                }
                "DeliveryCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DeliveryCompletedEvent::class.java)
                    eventHandler.onDeliveryCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
