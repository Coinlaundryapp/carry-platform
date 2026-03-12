package com.carry.serviceavailability.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.serviceavailability.domain.model.ServiceArea
import com.carry.serviceavailability.domain.vo.AreaStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "service_areas")
class ServiceAreaJpaEntity(
    @Column(nullable = false, unique = true, length = 20)
    val areaCode: String,

    @Column(nullable = false, length = 50)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AreaStatus,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "service_area_id")
    val schedules: MutableList<OperatingScheduleJpaEntity> = mutableListOf(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "service_area_id")
    val holidays: MutableList<HolidayOverrideJpaEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): ServiceArea = ServiceArea.reconstitute(
        id = id,
        areaCode = areaCode,
        name = name,
        status = status,
        schedules = schedules.map { it.toDomain() },
        holidays = holidays.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(area: ServiceArea) {
        status = area.status
        schedules.clear()
        schedules.addAll(area.schedules.map { OperatingScheduleJpaEntity.fromDomain(it) })
        holidays.clear()
        holidays.addAll(area.holidays.map { HolidayOverrideJpaEntity.fromDomain(it) })
    }

    companion object {
        fun fromDomain(area: ServiceArea): ServiceAreaJpaEntity = ServiceAreaJpaEntity(
            areaCode = area.areaCode,
            name = area.name,
            status = area.status,
            schedules = area.schedules.map { OperatingScheduleJpaEntity.fromDomain(it) }.toMutableList(),
            holidays = area.holidays.map { HolidayOverrideJpaEntity.fromDomain(it) }.toMutableList(),
        )
    }
}
