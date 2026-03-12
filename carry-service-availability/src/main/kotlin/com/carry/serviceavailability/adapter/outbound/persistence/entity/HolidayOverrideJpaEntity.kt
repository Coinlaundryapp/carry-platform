package com.carry.serviceavailability.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.serviceavailability.domain.model.HolidayOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "service_area_holidays")
class HolidayOverrideJpaEntity(
    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    val reason: String,
) : BaseEntity() {

    fun toDomain(): HolidayOverride = HolidayOverride.reconstitute(
        id = id,
        date = date,
        reason = reason,
    )

    companion object {
        fun fromDomain(holiday: HolidayOverride): HolidayOverrideJpaEntity =
            HolidayOverrideJpaEntity(
                date = holiday.date,
                reason = holiday.reason,
            )
    }
}
