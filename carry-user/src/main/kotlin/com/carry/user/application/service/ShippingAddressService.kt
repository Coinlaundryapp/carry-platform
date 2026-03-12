package com.carry.user.application.service

import com.carry.user.application.port.inbound.ShippingAddressUseCase
import com.carry.user.application.port.outbound.ShippingAddressPersistencePort
import com.carry.user.domain.exception.DefaultAddressDeletionException
import com.carry.user.domain.exception.ShippingAddressLimitExceededException
import com.carry.user.domain.exception.ShippingAddressNotFoundException
import com.carry.user.domain.exception.ShippingAddressNotOwnedException
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ShippingAddressService(
    private val shippingAddressPersistencePort: ShippingAddressPersistencePort,
) : ShippingAddressUseCase {

    override fun getAddress(userId: Long, addressId: Long): ShippingAddress {
        return findOwnedAddress(userId, addressId)
    }

    override fun getAddresses(userId: Long): List<ShippingAddress> {
        return shippingAddressPersistencePort.findByUserId(userId)
    }

    @Transactional
    override fun createAddress(
        userId: Long,
        alias: String,
        address: Address,
        coordinates: Coordinates,
        recipientName: String,
        recipientPhone: String,
        entranceInfo: String?,
        areaCode: String,
    ): ShippingAddress {
        val currentCount = shippingAddressPersistencePort.countByUserId(userId)
        if (currentCount >= ShippingAddress.MAX_ADDRESSES_PER_USER) {
            throw ShippingAddressLimitExceededException()
        }

        val isFirst = currentCount == 0L
        val shippingAddress = ShippingAddress.create(
            userId = userId,
            alias = alias,
            address = address,
            coordinates = coordinates,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            entranceInfo = entranceInfo,
            areaCode = areaCode,
            isDefault = isFirst,
        )
        return shippingAddressPersistencePort.save(shippingAddress)
    }

    @Transactional
    override fun updateAddress(
        userId: Long,
        addressId: Long,
        alias: String,
        address: Address,
        coordinates: Coordinates,
        recipientName: String,
        recipientPhone: String,
        entranceInfo: String?,
        areaCode: String,
    ): ShippingAddress {
        val shippingAddress = findOwnedAddress(userId, addressId)
        shippingAddress.update(alias, address, coordinates, recipientName, recipientPhone, entranceInfo, areaCode)
        return shippingAddressPersistencePort.save(shippingAddress)
    }

    @Transactional
    override fun deleteAddress(userId: Long, addressId: Long) {
        val address = findOwnedAddress(userId, addressId)

        if (address.isDefault) {
            val otherExists = shippingAddressPersistencePort.countByUserId(userId) > 1
            if (otherExists) throw DefaultAddressDeletionException()
        }

        shippingAddressPersistencePort.delete(address)
    }

    @Transactional
    override fun setDefaultAddress(userId: Long, addressId: Long) {
        val currentDefault = shippingAddressPersistencePort.findDefaultByUserId(userId)
        if (currentDefault != null && currentDefault.id != addressId) {
            currentDefault.unmarkAsDefault()
            shippingAddressPersistencePort.save(currentDefault)
        }

        val address = findOwnedAddress(userId, addressId)
        address.markAsDefault()
        shippingAddressPersistencePort.save(address)
    }

    private fun findOwnedAddress(userId: Long, addressId: Long): ShippingAddress {
        val address = shippingAddressPersistencePort.findById(addressId)
            ?: throw ShippingAddressNotFoundException(addressId)

        if (address.userId != userId) throw ShippingAddressNotOwnedException()

        return address
    }
}
