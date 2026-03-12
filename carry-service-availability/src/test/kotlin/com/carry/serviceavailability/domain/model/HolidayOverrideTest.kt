package com.carry.serviceavailability.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HolidayOverrideTest {

    @Test
    fun `휴무일을 생성한다`() {
        val holiday = HolidayOverride.create(LocalDate.of(2026, 1, 1), "신정")

        assertThat(holiday.id).isNull()
        assertThat(holiday.date).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(holiday.reason).isEqualTo("신정")
    }

    @Test
    fun `사유가 비어있으면 예외가 발생한다`() {
        assertThatThrownBy {
            HolidayOverride.create(LocalDate.of(2026, 1, 1), "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("휴무 사유")
    }

    @Test
    fun `사유가 공백만이면 예외가 발생한다`() {
        assertThatThrownBy {
            HolidayOverride.create(LocalDate.of(2026, 1, 1), "   ")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("휴무 사유")
    }
}
