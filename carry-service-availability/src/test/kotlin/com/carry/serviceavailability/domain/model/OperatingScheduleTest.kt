package com.carry.serviceavailability.domain.model

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class OperatingScheduleTest {

    @Nested
    inner class Create {

        @Test
        fun `운영 스케줄을 생성한다`() {
            val schedule = OperatingSchedule.create(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
            )

            assertThat(schedule.id).isNull()
            assertThat(schedule.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
            assertThat(schedule.openTime).isEqualTo(LocalTime.of(9, 0))
            assertThat(schedule.closeTime).isEqualTo(LocalTime.of(18, 0))
        }

        @Test
        fun `시작 시간이 종료 시간보다 늦으면 예외가 발생한다`() {
            assertThatThrownBy {
                OperatingSchedule.create(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0))
            }.isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `운영 시간을 변경한다`() {
            val schedule = OperatingSchedule.create(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
            )

            schedule.update(LocalTime.of(10, 0), LocalTime.of(20, 0))

            assertThat(schedule.openTime).isEqualTo(LocalTime.of(10, 0))
            assertThat(schedule.closeTime).isEqualTo(LocalTime.of(20, 0))
        }

        @Test
        fun `변경 시 시작 시간이 종료 시간보다 늦으면 예외가 발생한다`() {
            val schedule = OperatingSchedule.create(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
            )

            assertThatThrownBy {
                schedule.update(LocalTime.of(20, 0), LocalTime.of(10, 0))
            }.isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    inner class TimeSlot {

        @Test
        fun `운영 시간 내 시각은 TimeSlot에 포함된다`() {
            val schedule = OperatingSchedule.create(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
            )

            assertThat(schedule.timeSlot.contains(LocalTime.of(12, 0))).isTrue()
        }

        @Test
        fun `운영 시간 외 시각은 TimeSlot에 포함되지 않는다`() {
            val schedule = OperatingSchedule.create(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
            )

            assertThat(schedule.timeSlot.contains(LocalTime.of(7, 0))).isFalse()
        }
    }
}
