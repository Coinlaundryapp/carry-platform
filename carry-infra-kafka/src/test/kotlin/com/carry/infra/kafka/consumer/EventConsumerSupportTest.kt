package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 멱등성 회귀 — `EventConsumerSupport.processIfNotDuplicate`의 제어 흐름 단위 테스트(L1).
 *
 * 보호 불변식: 동일 eventId가 2회 이상 들어와도 부수효과(block)는 최대 1회.
 * 여기서는 DB 없이 분기 로직만 결정적으로 고정한다. 실영속/동시성은 carry-app 통합테스트에서 검증.
 */
class EventConsumerSupportTest {

    private val processedEventRepository = mockk<ProcessedEventRepository>()
    private val tracer = mockk<Tracer>(relaxed = true)
    private val support = EventConsumerSupport(processedEventRepository, tracer)

    @Test
    fun `신규 이벤트면 block을 실행하고 ProcessedEvent로 마킹한다`() {
        every { processedEventRepository.existsById("evt-1") } returns false
        val saved = slot<ProcessedEvent>()
        every { processedEventRepository.save(capture(saved)) } answers { saved.captured }

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(1)
        verify(exactly = 1) { processedEventRepository.save(any()) }
        assertThat(saved.captured.id).isEqualTo("evt-1")
    }

    @Test
    fun `이미 처리된 이벤트면 block을 실행하지 않고 저장도 하지 않는다`() {
        every { processedEventRepository.existsById("evt-1") } returns true

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(0)
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `block이 예외를 던지면 전파되고 ProcessedEvent로 마킹하지 않는다`() {
        every { processedEventRepository.existsById("evt-1") } returns false

        // 실패한 이벤트가 '처리됨'으로 마킹되면 at-least-once 재처리 시 영구 유실된다.
        assertThatThrownBy {
            support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") {
                throw IllegalStateException("downstream failure")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        verify(exactly = 0) { processedEventRepository.save(any()) }
    }
}
