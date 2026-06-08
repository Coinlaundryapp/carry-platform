package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import java.time.Instant

class FakeServiceAvailabilityQueryPort : ServiceAvailabilityQueryPort {

    /** 가용으로 표시된 (areaCode) 집합 */
    private val available = mutableSetOf<String>()
    /** 이 시각엔 불가로 표시된 (areaCode, instant) */
    private val unavailableInstants = mutableSetOf<Pair<String, Instant>>()

    fun markAvailable(areaCode: String) {
        available += areaCode
    }

    fun markUnavailableAt(areaCode: String, instant: Instant) {
        available += areaCode
        unavailableInstants += areaCode to instant
    }

    override fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant) {
        if (areaCode !in available) {
            throw IllegalStateException("서비스 지역 없음: $areaCode")
        }
        if (areaCode to pickupAt in unavailableInstants || areaCode to deliveryAt in unavailableInstants) {
            throw IllegalStateException("운영시간 밖: $areaCode")
        }
    }
}
