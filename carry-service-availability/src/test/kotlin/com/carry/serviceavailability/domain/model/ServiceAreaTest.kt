package com.carry.serviceavailability.domain.model

import com.carry.serviceavailability.domain.exception.AreaNotActiveException
import com.carry.serviceavailability.domain.exception.HolidayException
import com.carry.serviceavailability.domain.exception.OutsideOperatingHoursException
import com.carry.serviceavailability.domain.vo.AreaStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ServiceAreaTest {

    private val koreaZone = ZoneId.of("Asia/Seoul")

    private fun createArea() = ServiceArea.create("GANGNAM", "강남구")

    private fun activeAreaWithSchedule(): ServiceArea {
        val area = createArea()
        area.activate()
        DayOfWeek.entries.forEach { day ->
            area.setSchedule(day, LocalTime.of(8, 0), LocalTime.of(22, 0))
        }
        return area
    }

    @Nested
    inner class Create {

        @Test
        fun `서비스 지역을 생성하면 INACTIVE 상태이다`() {
            val area = createArea()
            assertThat(area.status).isEqualTo(AreaStatus.INACTIVE)
            assertThat(area.areaCode).isEqualTo("GANGNAM")
            assertThat(area.name).isEqualTo("강남구")
            assertThat(area.schedules).isEmpty()
            assertThat(area.holidays).isEmpty()
        }

        @Test
        fun `지역 코드가 비어있으면 예외가 발생한다`() {
            assertThatThrownBy { ServiceArea.create("", "강남구") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("지역 코드")
        }

        @Test
        fun `지역명이 비어있으면 예외가 발생한다`() {
            assertThatThrownBy { ServiceArea.create("GANGNAM", "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("지역명")
        }
    }

    @Nested
    inner class StatusTransitions {

        @Test
        fun `INACTIVE에서 ACTIVE로 활성화할 수 있다`() {
            val area = createArea()
            area.activate()
            assertThat(area.status).isEqualTo(AreaStatus.ACTIVE)
        }

        @Test
        fun `ACTIVE에서 INACTIVE로 비활성화할 수 있다`() {
            val area = createArea()
            area.activate()
            area.deactivate()
            assertThat(area.status).isEqualTo(AreaStatus.INACTIVE)
        }

        @Test
        fun `ACTIVE에서 SUSPENDED로 중지할 수 있다`() {
            val area = createArea()
            area.activate()
            area.suspend()
            assertThat(area.status).isEqualTo(AreaStatus.SUSPENDED)
        }
    }

    @Nested
    inner class ScheduleManagement {

        @Test
        fun `요일별 운영 시간을 설정한다`() {
            val area = createArea()
            area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))

            assertThat(area.schedules).hasSize(1)
            assertThat(area.schedules[0].dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
            assertThat(area.schedules[0].openTime).isEqualTo(LocalTime.of(9, 0))
            assertThat(area.schedules[0].closeTime).isEqualTo(LocalTime.of(18, 0))
        }

        @Test
        fun `같은 요일에 다시 설정하면 덮어쓴다`() {
            val area = createArea()
            area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))
            area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))

            assertThat(area.schedules).hasSize(1)
            assertThat(area.schedules[0].openTime).isEqualTo(LocalTime.of(10, 0))
        }

        @Test
        fun `운영 시간을 삭제한다`() {
            val area = createArea()
            area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))
            area.removeSchedule(DayOfWeek.MONDAY)

            assertThat(area.schedules).isEmpty()
        }

        @Test
        fun `시작 시간이 종료 시간보다 늦으면 예외가 발생한다`() {
            val area = createArea()
            assertThatThrownBy {
                area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0))
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("운영 시작 시간")
        }
    }

    @Nested
    inner class HolidayManagement {

        @Test
        fun `휴무일을 추가한다`() {
            val area = createArea()
            area.addHoliday(LocalDate.of(2026, 1, 1), "신정")

            assertThat(area.holidays).hasSize(1)
            assertThat(area.holidays[0].date).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(area.holidays[0].reason).isEqualTo("신정")
        }

        @Test
        fun `같은 날짜에 다시 추가하면 덮어쓴다`() {
            val area = createArea()
            area.addHoliday(LocalDate.of(2026, 1, 1), "신정")
            area.addHoliday(LocalDate.of(2026, 1, 1), "신년 연휴")

            assertThat(area.holidays).hasSize(1)
            assertThat(area.holidays[0].reason).isEqualTo("신년 연휴")
        }

        @Test
        fun `휴무일을 삭제한다`() {
            val area = createArea()
            area.addHoliday(LocalDate.of(2026, 1, 1), "신정")
            area.removeHoliday(LocalDate.of(2026, 1, 1))

            assertThat(area.holidays).isEmpty()
        }
    }

    @Nested
    inner class CheckAvailability {

        @Test
        fun `ACTIVE 상태 + 운영 시간 내 요청 시 예외가 발생하지 않는다`() {
            val area = activeAreaWithSchedule()
            val requestedAt = ZonedDateTime.of(2026, 3, 12, 14, 0, 0, 0, koreaZone).toInstant()

            area.checkAvailability(requestedAt, koreaZone)
        }

        @Test
        fun `INACTIVE 상태이면 AreaNotActiveException이 발생한다`() {
            val area = createArea()
            val requestedAt = Instant.now()

            assertThatThrownBy { area.checkAvailability(requestedAt, koreaZone) }
                .isInstanceOf(AreaNotActiveException::class.java)
        }

        @Test
        fun `SUSPENDED 상태이면 AreaNotActiveException이 발생한다`() {
            val area = createArea()
            area.activate()
            area.suspend()
            val requestedAt = Instant.now()

            assertThatThrownBy { area.checkAvailability(requestedAt, koreaZone) }
                .isInstanceOf(AreaNotActiveException::class.java)
        }

        @Test
        fun `휴무일에 요청하면 HolidayException이 발생한다`() {
            val area = activeAreaWithSchedule()
            area.addHoliday(LocalDate.of(2026, 3, 15), "임시 휴무")
            val requestedAt = ZonedDateTime.of(2026, 3, 15, 14, 0, 0, 0, koreaZone).toInstant()

            assertThatThrownBy { area.checkAvailability(requestedAt, koreaZone) }
                .isInstanceOf(HolidayException::class.java)
        }

        @Test
        fun `해당 요일에 운영 스케줄이 없으면 OutsideOperatingHoursException이 발생한다`() {
            val area = createArea()
            area.activate()
            // 월요일만 스케줄 설정
            area.setSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))

            // 화요일에 요청 (2026-03-17은 화요일)
            val tuesday = ZonedDateTime.of(2026, 3, 17, 14, 0, 0, 0, koreaZone).toInstant()

            assertThatThrownBy { area.checkAvailability(tuesday, koreaZone) }
                .isInstanceOf(OutsideOperatingHoursException::class.java)
        }

        @Test
        fun `운영 시간 외 요청 시 OutsideOperatingHoursException이 발생한다`() {
            val area = activeAreaWithSchedule()
            // 새벽 3시 (운영시간 8-22시 밖)
            val requestedAt = ZonedDateTime.of(2026, 3, 12, 3, 0, 0, 0, koreaZone).toInstant()

            assertThatThrownBy { area.checkAvailability(requestedAt, koreaZone) }
                .isInstanceOf(OutsideOperatingHoursException::class.java)
        }
    }

    @Nested
    inner class IsAvailable {

        @Test
        fun `가용한 시간에 true를 반환한다`() {
            val area = activeAreaWithSchedule()
            val requestedAt = ZonedDateTime.of(2026, 3, 12, 14, 0, 0, 0, koreaZone).toInstant()

            assertThat(area.isAvailable(requestedAt, koreaZone)).isTrue()
        }

        @Test
        fun `비가용 시간에 false를 반환한다`() {
            val area = createArea() // INACTIVE
            val requestedAt = Instant.now()

            assertThat(area.isAvailable(requestedAt, koreaZone)).isFalse()
        }
    }
}
