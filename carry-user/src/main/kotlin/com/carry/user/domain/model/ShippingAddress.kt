package com.carry.user.domain.model

import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user_shipping_addresses")
class ShippingAddress(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var alias: String,

    @Column(nullable = false)
    var roadAddress: String,

    @Column(nullable = false)
    var detailAddress: String,

    @Column(nullable = false)
    var zipCode: String,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    @Column(nullable = false)
    var isDefault: Boolean = false
) : BaseEntity() {

    fun update(alias: String, roadAddress: String, detailAddress: String, zipCode: String, latitude: Double, longitude: Double) {
        this.alias = alias
        this.roadAddress = roadAddress
        this.detailAddress = detailAddress
        this.zipCode = zipCode
        this.latitude = latitude
        this.longitude = longitude
    }

    fun setAsDefault() {
        this.isDefault = true
    }

    fun unsetDefault() {
        this.isDefault = false
    }
}
