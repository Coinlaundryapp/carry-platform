package com.carry.app.adapter

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.user.application.port.inbound.ShippingAddressUseCase
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-order -> carry-user
 *
 * Implements the UserQueryPort defined in carry-order by delegating to carry-user's ShippingAddressUseCase.
 */
@Component
class UserQueryPortAdapter(
    private val shippingAddressUseCase: ShippingAddressUseCase,
) : UserQueryPort {

    override fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress {
        val address = shippingAddressUseCase.getAddress(userId, addressId)

        return OrderShippingAddress(
            roadAddress = address.address.roadAddress,
            detailAddress = address.address.detailAddress,
            zipCode = address.address.zipCode,
            latitude = address.coordinates.latitude,
            longitude = address.coordinates.longitude,
            recipientName = address.recipientName,
            recipientPhone = address.recipientPhone,
            entranceInfo = address.entranceInfo,
            areaCode = address.areaCode,
        )
    }
}
