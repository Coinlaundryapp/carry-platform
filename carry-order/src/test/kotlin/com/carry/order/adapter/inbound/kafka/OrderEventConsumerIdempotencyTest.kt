package com.carry.order.adapter.inbound.kafka

import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.carry.infra.kafka.consumer.ProcessedEventRepository
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test

/**
 * 멱등성 회귀 — 컨슈머 dedup 키 배선 검증(L2).
 *
 * 대표 컨슈머 `OrderEventConsumer`가 dedup 키로 Kafka 레코드의 offset/partition이 아니라
 * **안정적 고유 `envelope.id`** 를 넘기는지 증명한다. 동일 `id`·다른 offset의 레코드 2개를
 * 공급해도 saga 핸들러는 1회만 호출되어야 한다.
 *
 * `EventConsumerSupport`는 실제 객체를 쓰되, 저장소만 in-memory set 기반 fake로 두어 dedup을
 * 결정적으로 재현한다(Spring 컨텍스트·DB 불필요).
 */
class OrderEventConsumerIdempotencyTest {

    private val objectMapper = jacksonObjectMapper()
    private val sagaHandler = mockk<OrderSagaEventHandler>(relaxed = true)

    /** claim()이 실제로 동작하는 in-memory fake (키 = eventId). 최초 1, 이후 0. */
    private fun inMemoryEventConsumerSupport(): EventConsumerSupport {
        val processedIds = mutableSetOf<String>()
        val repo = mockk<ProcessedEventRepository>()
        every { repo.claim(any<String>(), any<Instant>()) } answers {
            if (processedIds.add(firstArg<String>())) 1 else 0
        }
        return EventConsumerSupport(repo, mockk<Tracer>(relaxed = true))
    }

    private fun dispatchAcceptedRecord(envelopeId: String, offset: Long): ConsumerRecord<String, String> {
        val envelope = OutboxEventEnvelope(
            id = envelopeId,
            aggregateType = "Dispatch",
            aggregateId = "1",
            eventType = "DispatchAcceptedEvent",
            payload = objectMapper.writeValueAsString(
                DispatchAcceptedEvent(dispatchId = 1L, orderId = 10L, carrierId = 100L, laundromatId = 1000L)
            ),
        )
        return ConsumerRecord("carry.Dispatch.events", 0, offset, envelopeId, objectMapper.writeValueAsString(envelope))
    }

    @Test
    fun `동일 envelope_id를 다른 offset으로 두 번 받아도 saga 핸들러는 한 번만 호출된다`() {
        val consumer = OrderEventConsumer(sagaHandler, inMemoryEventConsumerSupport(), objectMapper)

        // 같은 이벤트(envelope.id 동일), 다른 Kafka offset → 재배달/리밸런스 중복을 모사.
        consumer.consumeDispatchEvents(dispatchAcceptedRecord("dispatch-evt-1", offset = 42L))
        consumer.consumeDispatchEvents(dispatchAcceptedRecord("dispatch-evt-1", offset = 99L))

        verify(exactly = 1) { sagaHandler.onDispatchAccepted(any()) }
    }
}
