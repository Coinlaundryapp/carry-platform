package com.carry.user.application.service

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.user.application.dto.CreateShippingAddressCommand
import com.carry.user.application.dto.ShippingAddressResponse
import com.carry.user.application.dto.UpdateShippingAddressCommand
import com.carry.user.application.port.inbound.ShippingAddressUseCase
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.repository.ShippingAddressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ShippingAddressService(
    private val shippingAddressRepository: ShippingAddressRepository
) : ShippingAddressUseCase {

    override fun getAddresses(userId: Long): List<ShippingAddressResponse> {
        return shippingAddressRepository.findByUserId(userId)
            .map { ShippingAddressResponse.from(it) }
    }

    @Transactional
    override fun createAddress(userId: Long, command: CreateShippingAddressCommand): ShippingAddressResponse {
        val isFirst = shippingAddressRepository.countByUserId(userId) == 0L
        val address = ShippingAddress(
            userId = userId,
            alias = command.alias,
            roadAddress = command.roadAddress,
            detailAddress = command.detailAddress,
            zipCode = command.zipCode,
            latitude = command.latitude,
            longitude = command.longitude,
            isDefault = isFirst
        )
        return ShippingAddressResponse.from(shippingAddressRepository.save(address))
    }

    @Transactional
    override fun updateAddress(userId: Long, addressId: Long, command: UpdateShippingAddressCommand): ShippingAddressResponse {
        val address = findUserAddress(userId, addressId)
        address.update(command.alias, command.roadAddress, command.detailAddress, command.zipCode, command.latitude, command.longitude)
        return ShippingAddressResponse.from(address)
    }

    @Transactional
    override fun deleteAddress(userId: Long, addressId: Long) {
        val address = findUserAddress(userId, addressId)
        shippingAddressRepository.delete(address)
    }

    @Transactional
    override fun setDefaultAddress(userId: Long, addressId: Long) {
        val current = shippingAddressRepository.findByUserIdAndIsDefaultTrue(userId)
        current?.unsetDefault()
        val address = findUserAddress(userId, addressId)
        address.setAsDefault()
    }

    private fun findUserAddress(userId: Long, addressId: Long): ShippingAddress {
        val address = shippingAddressRepository.findById(addressId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "Shipping address not found") }
        if (address.userId != userId) {
            throw BusinessException(ErrorCode.FORBIDDEN, "Not your address")
        }
        return address
    }
}
