package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

abstract class ServiceAvailabilityQueryPortContract {

    protected val areaCode = "GANGNAM"
    // 2026-06-08 12:00 KST = 03:00Z (월요일), delivery 익일 정오(화요일)
    protected val pickupAt: Instant = Instant.parse("2026-06-08T03:00:00Z")
    protected val deliveryAt: Instant = Instant.parse("2026-06-09T03:00:00Z")

    protected abstract fun subject(): ServiceAvailabilityQueryPort

    /** areaCode가 ACTIVE이고 pickup·delivery 두 시각 모두 운영시간 내인 상태 */
    protected abstract fun arrangeAvailable()

    /** areaCode 자체가 존재하지 않는 상태 */
    protected abstract fun arrangeMissingArea()

    /** areaCode는 있으나 delivery 시각이 운영시간 밖인 상태 */
    protected abstract fun arrangeDeliveryOutsideHours()

    @Test
    fun `가용하면 예외 없이 통과`() {
        arrangeAvailable()
        assertThatCode { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `area가 없으면 예외`() {
        arrangeMissingArea()
        assertThatThrownBy { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `delivery 시각이 운영시간 밖이면 예외 (두 instant 모두 검증)`() {
        arrangeDeliveryOutsideHours()
        assertThatThrownBy { subject().checkAvailability(areaCode, pickupAt, deliveryAt) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
