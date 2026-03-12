package com.carry.user.application.port.inbound

import com.carry.user.application.dto.CreateShippingAddressCommand
import com.carry.user.application.dto.ShippingAddressResponse
import com.carry.user.application.dto.UpdateShippingAddressCommand

interface ShippingAddressUseCase {
    fun getAddresses(userId: Long): List<ShippingAddressResponse>
    fun createAddress(userId: Long, command: CreateShippingAddressCommand): ShippingAddressResponse
    fun updateAddress(userId: Long, addressId: Long, command: UpdateShippingAddressCommand): ShippingAddressResponse
    fun deleteAddress(userId: Long, addressId: Long)
    fun setDefaultAddress(userId: Long, addressId: Long)
}
