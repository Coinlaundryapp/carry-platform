package com.carry.app.contract

import com.carry.app.adapter.ServiceAvailabilityQueryPortAdapter
import com.carry.app.contract.fake.FakeServiceAreaPersistencePort
import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.order.application.port.outbound.contract.ServiceAvailabilityQueryPortContract
import com.carry.serviceavailability.application.service.ServiceAvailabilityQueryService
import com.carry.serviceavailability.domain.model.ServiceArea
import java.time.DayOfWeek
import java.time.LocalTime

class ServiceAvailabilityQueryPortAdapterContractTest : ServiceAvailabilityQueryPortContract() {

    private val persistence = FakeServiceAreaPersistencePort()
    private val adapter = ServiceAvailabilityQueryPortAdapter(ServiceAvailabilityQueryService(persistence))

    override fun subject(): ServiceAvailabilityQueryPort = adapter

    override fun arrangeAvailable() {
        persistence.put(activeAllWeekArea())
    }

    override fun arrangeMissingArea() {
        // put 하지 않음
    }

    override fun arrangeDeliveryOutsideHours() {
        // pickupAt(월 12:00 KST)은 전일 운영으로 통과, deliveryAt(화 12:00 KST)는 좁은 슬롯 밖
        val area = ServiceArea.create(areaCode, "강남구").apply {
            activate()
            // pickup 요일(월) 전일 운영
            setSchedule(pickupAt.atZone(KST).dayOfWeek, LocalTime.MIN, LocalTime.of(23, 59))
            // delivery 요일(화) 좁은 슬롯 — 정오 미포함
            setSchedule(deliveryAt.atZone(KST).dayOfWeek, LocalTime.of(11, 0), LocalTime.of(11, 30))
        }
        persistence.put(area)
    }

    override fun arrangePickupOutsideHours() {
        // deliveryAt(화 12:00 KST)은 전일 운영으로 통과, pickupAt(월 12:00 KST)는 좁은 슬롯 밖
        val area = ServiceArea.create(areaCode, "강남구").apply {
            activate()
            // delivery 요일(화) 전일 운영
            setSchedule(deliveryAt.atZone(KST).dayOfWeek, LocalTime.MIN, LocalTime.of(23, 59))
            // pickup 요일(월) 좁은 슬롯 — 정오 미포함
            setSchedule(pickupAt.atZone(KST).dayOfWeek, LocalTime.of(11, 0), LocalTime.of(11, 30))
        }
        persistence.put(area)
    }

    private fun activeAllWeekArea(): ServiceArea =
        ServiceArea.create(areaCode, "강남구").apply {
            activate()
            DayOfWeek.entries.forEach { setSchedule(it, LocalTime.MIN, LocalTime.of(23, 59)) }
        }

    companion object {
        private val KST = java.time.ZoneId.of("Asia/Seoul")
    }
}
