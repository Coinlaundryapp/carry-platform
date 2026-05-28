package com.carry.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class SagaLogContextTest {

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `withOrderId는 블록 내부에서 MDC에 orderId 문자열을 넣고 종료 시 제거한다`() {
        var captured: String? = null

        SagaLogContext.withOrderId(42L) {
            captured = MDC.get(SagaLogContext.KEY_ORDER_ID)
        }

        assertThat(captured).isEqualTo("42")
        assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isNull()
    }

    @Test
    fun `예외가 발생해도 MDC orderId는 정리된다`() {
        runCatching {
            SagaLogContext.withOrderId(99L) {
                throw RuntimeException("intentional")
            }
        }

        assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isNull()
    }

    @Test
    fun `중첩 호출 시 이전 orderId를 보존하고 복구한다`() {
        SagaLogContext.withOrderId(1L) {
            assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isEqualTo("1")

            SagaLogContext.withOrderId(2L) {
                assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isEqualTo("2")
            }

            assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isEqualTo("1")
        }

        assertThat(MDC.get(SagaLogContext.KEY_ORDER_ID)).isNull()
    }

    @Test
    fun `블록의 반환값을 그대로 돌려준다`() {
        val result = SagaLogContext.withOrderId(7L) { "ok" }

        assertThat(result).isEqualTo("ok")
    }
}
