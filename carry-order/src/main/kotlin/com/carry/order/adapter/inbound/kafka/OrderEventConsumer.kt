package com.carry.order.adapter.inbound.kafka

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val sagaHandler: OrderSagaEventHandler,
    private val eventConsumerSupport: EventConsumerSupport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["carry.Dispatch.events"], groupId = "carry-order-module")
    fun consumeDispatchEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "DispatchAcceptedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchAcceptedEvent::class.java)
                    sagaHandler.onDispatchAccepted(event)
                }
                "DispatchTimeoutEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchTimeoutEvent::class.java)
                    sagaHandler.onDispatchTimeout(event)
                }
                "DispatchCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DispatchCancelledEvent::class.java)
                    sagaHandler.onDispatchCancelled(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }

    @KafkaListener(topics = ["carry.Delivery.events"], groupId = "carry-order-module")
    fun consumeDeliveryEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "PickupCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PickupCompletedEvent::class.java)
                    sagaHandler.onPickupCompleted(event)
                }
                "LaundryStartedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, LaundryStartedEvent::class.java)
                    sagaHandler.onLaundryStarted(event)
                }
                "DeliveryCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, DeliveryCompletedEvent::class.java)
                    sagaHandler.onDeliveryCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }

    @KafkaListener(topics = ["carry.Payment.events"], groupId = "carry-order-module")
    fun consumePaymentEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate(envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "InvoiceIssuedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, InvoiceIssuedEvent::class.java)
                    sagaHandler.onInvoiceIssued(event)
                }
                "PaymentCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PaymentCompletedEvent::class.java)
                    sagaHandler.onPaymentCompleted(event)
                }
                "PaymentFailedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PaymentFailedEvent::class.java)
                    sagaHandler.onPaymentFailed(event)
                }
                "RefundCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, RefundCompletedEvent::class.java)
                    sagaHandler.onRefundCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
