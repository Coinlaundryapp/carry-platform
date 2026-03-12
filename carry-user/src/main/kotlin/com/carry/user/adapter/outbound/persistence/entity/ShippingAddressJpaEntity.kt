package com.carry.user.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user_shipping_addresses")
class ShippingAddressJpaEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var alias: String,

    @Column(name = "road_address", nullable = false)
    var roadAddress: String,

    @Column(name = "detail_address", nullable = false)
    var detailAddress: String,

    @Column(name = "zip_code", nullable = false)
    var zipCode: String,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    @Column(name = "recipient_name", nullable = false, length = 50)
    var recipientName: String,

    @Column(name = "recipient_phone", nullable = false, length = 20)
    var recipientPhone: String,

    @Column(name = "entrance_info")
    var entranceInfo: String? = null,

    @Column(name = "area_code", nullable = false, length = 20)
    var areaCode: String,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
) : BaseEntity() {

    fun toDomain(): ShippingAddress = ShippingAddress.reconstitute(
        id = id,
        userId = userId,
        alias = alias,
        address = Address(roadAddress, detailAddress, zipCode),
        coordinates = Coordinates(latitude, longitude),
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        entranceInfo = entranceInfo,
        areaCode = areaCode,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(address: ShippingAddress) {
        alias = address.alias
        roadAddress = address.address.roadAddress
        detailAddress = address.address.detailAddress
        zipCode = address.address.zipCode
        latitude = address.coordinates.latitude
        longitude = address.coordinates.longitude
        recipientName = address.recipientName
        recipientPhone = address.recipientPhone
        entranceInfo = address.entranceInfo
        areaCode = address.areaCode
        isDefault = address.isDefault
    }

    companion object {
        fun fromDomain(address: ShippingAddress): ShippingAddressJpaEntity =
            ShippingAddressJpaEntity(
                userId = address.userId,
                alias = address.alias,
                roadAddress = address.address.roadAddress,
                detailAddress = address.address.detailAddress,
                zipCode = address.address.zipCode,
                latitude = address.coordinates.latitude,
                longitude = address.coordinates.longitude,
                recipientName = address.recipientName,
                recipientPhone = address.recipientPhone,
                entranceInfo = address.entranceInfo,
                areaCode = address.areaCode,
                isDefault = address.isDefault,
            )
    }
}
