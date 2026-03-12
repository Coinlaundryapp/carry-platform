package com.carry.serviceavailability.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.serviceavailability.domain.model.OperatingSchedule
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime

@Entity
@Table(name = "service_area_schedules")
class OperatingScheduleJpaEntity(
    @Column(nullable = false)
    val dayOfWeek: Int,

    @Column(nullable = false)
    val openTime: LocalTime,

    @Column(nullable = false)
    val closeTime: LocalTime,
) : BaseEntity() {

    fun toDomain(): OperatingSchedule = OperatingSchedule.reconstitute(
        id = id,
        dayOfWeek = DayOfWeek.of(dayOfWeek),
        openTime = openTime,
        closeTime = closeTime,
    )

    companion object {
        fun fromDomain(schedule: OperatingSchedule): OperatingScheduleJpaEntity =
            OperatingScheduleJpaEntity(
                dayOfWeek = schedule.dayOfWeek.value,
                openTime = schedule.openTime,
                closeTime = schedule.closeTime,
            )
    }
}
