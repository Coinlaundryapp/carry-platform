package com.carry.app.contract

import com.carry.app.adapter.UserQueryPortAdapter
import com.carry.app.contract.fake.FakeShippingAddressPersistencePort
import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.application.port.outbound.contract.UserQueryPortContract
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.user.application.service.ShippingAddressService
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import java.time.Instant

class UserQueryPortAdapterContractTest : UserQueryPortContract() {

    private val persistence = FakeShippingAddressPersistencePort()
    private val adapter = UserQueryPortAdapter(ShippingAddressService(persistence))

    override fun subject(): UserQueryPort = adapter

    override fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress) {
        persistence.put(
            addressId,
            ShippingAddress.reconstitute(
                id = addressId,
                userId = userId,
                alias = "집",
                address = Address(
                    roadAddress = expected.roadAddress,
                    detailAddress = expected.detailAddress,
                    zipCode = requireNotNull(expected.zipCode) { "계약상 zipCode는 non-null" },
                ),
                coordinates = Coordinates(expected.latitude, expected.longitude),
                recipientName = expected.recipientName,
                recipientPhone = expected.recipientPhone,
                entranceInfo = expected.entranceInfo,
                areaCode = expected.areaCode,
                isDefault = false,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    override fun arrangeMissing(userId: Long, addressId: Long) {
        // put 하지 않음 → findById null → ShippingAddressNotFoundException 전파
    }
}
