package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 멱등성 회귀 — `EventConsumerSupport.processIfNotDuplicate`의 제어 흐름 단위 테스트(L1).
 *
 * 보호 불변식: claim-first. 동일 eventId가 2회 이상 들어와도 부수효과(block)는 최대 1회.
 * 여기서는 DB 없이 분기·순서 로직만 결정적으로 고정한다(claim 성공/중복/예외 전파, claim이
 * block보다 먼저 호출됨). 실영속/동시성/롤백은 carry-app 통합테스트에서 검증.
 */
class EventConsumerSupportTest {

    private val processedEventRepository = mockk<ProcessedEventRepository>()
    private val tracer = mockk<Tracer>(relaxed = true)
    private val support = EventConsumerSupport(processedEventRepository, tracer)

    @Test
    fun `claim에 성공하면(1) block을 실행한다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 1

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(1)
        verify(exactly = 1) { processedEventRepository.claim(eq("evt-1"), any<Instant>()) }
    }

    @Test
    fun `claim이 중복이면(0) block을 실행하지 않는다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 0

        var executed = 0
        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { executed++ }

        assertThat(executed).isEqualTo(0)
    }

    @Test
    fun `claim은 block보다 먼저 호출된다 (claim-first)`() {
        val calls = mutableListOf<String>()
        // claim의 answers에서 호출 시점을 calls에 기록 → block의 기록과 순서를 비교한다.
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } answers {
            calls.add("claim"); 1
        }

        support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") { calls.add("block") }

        // 실제 실행 순서가 claim → block 임을 단언(claim-first의 핵심).
        assertThat(calls).containsExactly("claim", "block")
    }

    @Test
    fun `block이 예외를 던지면 전파된다`() {
        every { processedEventRepository.claim(eq("evt-1"), any<Instant>()) } returns 1

        // 롤백(claim 행 제거)은 @Transactional/실DB 관심사 → 통합 테스트에서 검증.
        // 단위에서는 예외 전파만 단언한다.
        assertThatThrownBy {
            support.processIfNotDuplicate("evt-1", eventType = "OrderCreatedEvent") {
                throw IllegalStateException("downstream failure")
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
