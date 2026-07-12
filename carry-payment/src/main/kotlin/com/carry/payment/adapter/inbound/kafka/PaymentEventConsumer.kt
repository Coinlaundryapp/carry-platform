package com.carry.payment.adapter.inbound.kafka

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.payment.InvoiceIssuedEvent
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
    fun consumeDeliveryEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-payment-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "PickupCompletedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, PickupCompletedEvent::class.java)
                    sagaHandler.onPickupCompleted(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }

    @KafkaListener(topics = ["carry.Order.events"], groupId = "carry-payment-module")
    fun consumeOrderEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-payment-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "OrderCancelledEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, OrderCancelledEvent::class.java)
                    sagaHandler.onOrderCancelled(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }

    /**
     * 이 코드베이스 최초의 **자체 소비(self-consumption)** 리스너 — carry-payment 가 자신이 발행한
     * `carry.Payment.events` 토픽을 다시 구독해 InvoiceIssuedEvent 로 자동과금 사가를 시작한다.
     * 같은 사가가 완료·실패 시 발행하는 PaymentCompletedEvent/PaymentFailedEvent 도 이 토픽으로
     * 되돌아오지만 아래 when 에서 라우팅되지 않으므로(else 분기 무동작) 무한 루프가 되지 않는다.
     */
    @KafkaListener(topics = ["carry.Payment.events"], groupId = "carry-payment-module")
    fun consumePaymentEvents(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), OutboxEventEnvelope::class.java)
        eventConsumerSupport.processIfNotDuplicate("carry-payment-module", envelope.id, envelope.traceId, envelope.eventType) {
            when (envelope.eventType) {
                "InvoiceIssuedEvent" -> {
                    val event = objectMapper.readValue(envelope.payload, InvoiceIssuedEvent::class.java)
                    sagaHandler.onInvoiceIssued(event)
                }
                else -> log.debug("Ignoring event type: {}", envelope.eventType)
            }
        }
    }
}
