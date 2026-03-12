package com.carry.laundromat.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

@Entity
@Table(name = "laundromat_laundromats")
class LaundromatJpaEntity(
    @Column(nullable = false)
    var name: String,

    @Column(name = "road_address", nullable = false)
    var roadAddress: String,

    @Column(name = "detail_address")
    var detailAddress: String? = null,

    @Column(name = "zip_code")
    var zipCode: String? = null,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "laundromat_options",
        joinColumns = [JoinColumn(name = "laundromat_id")],
    )
    @Column(name = "option")
    @Enumerated(EnumType.STRING)
    val options: MutableSet<LaundromatOption> = mutableSetOf(),

    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @JoinColumn(name = "laundromat_id")
    @OrderBy("id ASC")
    val mediaResources: MutableList<LaundromatMediaResourceJpaEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): Laundromat = Laundromat.reconstitute(
        id = id,
        name = name,
        address = LaundromatAddress(roadAddress, detailAddress, zipCode),
        location = Location(latitude, longitude),
        options = options.toSet(),
        mediaResources = mediaResources.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(laundromat: Laundromat) {
        name = laundromat.name
        roadAddress = laundromat.address.roadAddress
        detailAddress = laundromat.address.detailAddress
        zipCode = laundromat.address.zipCode
        latitude = laundromat.location.latitude
        longitude = laundromat.location.longitude

        options.clear()
        options.addAll(laundromat.options)

        mediaResources.clear()
        mediaResources.addAll(
            laundromat.mediaResources.map { LaundromatMediaResourceJpaEntity.fromDomain(it) },
        )
    }

    companion object {
        fun fromDomain(laundromat: Laundromat): LaundromatJpaEntity = LaundromatJpaEntity(
            name = laundromat.name,
            roadAddress = laundromat.address.roadAddress,
            detailAddress = laundromat.address.detailAddress,
            zipCode = laundromat.address.zipCode,
            latitude = laundromat.location.latitude,
            longitude = laundromat.location.longitude,
            options = laundromat.options.toMutableSet(),
            mediaResources = laundromat.mediaResources
                .map { LaundromatMediaResourceJpaEntity.fromDomain(it) }
                .toMutableList(),
        )
    }
}
