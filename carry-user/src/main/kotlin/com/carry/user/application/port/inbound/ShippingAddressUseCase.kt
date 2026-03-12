package com.carry.user.application.port.inbound

import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates

interface ShippingAddressUseCase {

    fun getAddresses(userId: Long): List<ShippingAddress>

    fun createAddress(
        userId: Long,
        alias: String,
        address: Address,
        coordinates: Coordinates,
    ): ShippingAddress

    fun updateAddress(
        userId: Long,
        addressId: Long,
        alias: String,
        address: Address,
        coordinates: Coordinates,
    ): ShippingAddress

    fun deleteAddress(userId: Long, addressId: Long)

    fun setDefaultAddress(userId: Long, addressId: Long)
}
