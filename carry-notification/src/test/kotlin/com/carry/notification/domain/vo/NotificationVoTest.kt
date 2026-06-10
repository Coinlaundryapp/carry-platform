package com.carry.notification.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationVoTest {
    @Test
    fun `메시지는 제목과 내용을 담는다`() {
        val m = NotificationMessage("주문 접수", "주문이 접수되었습니다.")
        assertThat(m.title).isEqualTo("주문 접수")
        assertThat(m.content).isEqualTo("주문이 접수되었습니다.")
    }

    @Test
    fun `참조는 유형과 ID를 함께 담는다`() {
        val r = NotificationReference("ORDER", 100L)
        assertThat(r.type).isEqualTo("ORDER")
        assertThat(r.id).isEqualTo(100L)
    }
}
