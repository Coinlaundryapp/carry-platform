package com.carry.app.test

import com.carry.serviceavailability.domain.model.OperatingSchedule
import com.carry.serviceavailability.domain.model.ServiceArea
import com.carry.serviceavailability.domain.vo.AreaStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * TestFixtures의 희망 픽업/배송 시각이 **벽시계 time-of-day에 의존하지 않음**을 고정 검증한다.
 *
 * 회귀 방지: `now.plus(24h)`처럼 실행 시점의 time-of-day가 그대로 남으면, CI가 23:59 KST에 돌 때
 * 전일 운영(00:00~23:59) 종료 경계(23:59:00)를 넘겨 주문 생성 사가 통합테스트가 OutsideOperatingHours로
 * 무더기 거짓 RED가 된다(2026-06-15 발생). 시각을 고정 KST 시각으로 두면 시계와 무관하게 항상 통과한다.
 */
class TestFixturesScheduleTest {

    private val kst = ZoneId.of("Asia/Seoul")

    private val allDayArea: ServiceArea = ServiceArea.reconstitute(
        id = 1L,
        areaCode = "GANGNAM",
        name = "강남구",
        status = AreaStatus.ACTIVE,
        schedules = DayOfWeek.entries.map {
            OperatingSchedule.reconstitute(it.value.toLong(), it, LocalTime.of(0, 0), LocalTime.of(23, 59))
        },
        holidays = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `희망 픽업·배송 시각은 운영 경계에 걸리지 않는 고정 KST 시각이다`() {
        assertThat(TestFixtures.desiredPickupAt().atZone(kst).toLocalTime()).isEqualTo(LocalTime.of(10, 0))
        assertThat(TestFixtures.desiredDeliveryAt().atZone(kst).toLocalTime()).isEqualTo(LocalTime.of(14, 0))
    }

    @Test
    fun `희망 픽업·배송 시각은 전일(00-00~23-59) 운영 일정에서 항상 available 하다`() {
        assertThat(allDayArea.isAvailable(TestFixtures.desiredPickupAt())).isTrue()
        assertThat(allDayArea.isAvailable(TestFixtures.desiredDeliveryAt())).isTrue()
    }

    @Test
    fun `희망 배송 시각은 픽업 이후이고 둘 다 미래다`() {
        val pickup = TestFixtures.desiredPickupAt()
        val delivery = TestFixtures.desiredDeliveryAt()
        assertThat(pickup).isAfter(Instant.now())
        assertThat(delivery).isAfter(pickup)
    }
}
