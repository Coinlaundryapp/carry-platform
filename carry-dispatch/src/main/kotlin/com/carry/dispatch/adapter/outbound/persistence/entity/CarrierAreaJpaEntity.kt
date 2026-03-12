package com.carry.dispatch.adapter.outbound.persistence.entity

import com.carry.dispatch.domain.model.CarrierArea
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "dispatch_carrier_areas",
    uniqueConstraints = [UniqueConstraint(columnNames = ["carrierId", "areaCode"])],
)
class CarrierAreaJpaEntity(
    @Column(nullable = false)
    val carrierId: Long,

    @Column(nullable = false, length = 20)
    val areaCode: String,

    @Column(nullable = false, length = 50)
    val areaName: String,

    @Column(nullable = false)
    var active: Boolean,
) : BaseEntity() {

    fun toDomain(): CarrierArea = CarrierArea.reconstitute(
        id = id,
        carrierId = carrierId,
        areaCode = areaCode,
        areaName = areaName,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(carrierArea: CarrierArea) {
        active = carrierArea.active
    }

    companion object {
        fun fromDomain(carrierArea: CarrierArea): CarrierAreaJpaEntity = CarrierAreaJpaEntity(
            carrierId = carrierArea.carrierId,
            areaCode = carrierArea.areaCode,
            areaName = carrierArea.areaName,
            active = carrierArea.active,
        )
    }
}
