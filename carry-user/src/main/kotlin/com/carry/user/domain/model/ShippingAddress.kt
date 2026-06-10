package com.carry.user.domain.model

import com.carry.common.exception.requireInput
import com.carry.common.exception.checkState
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import com.carry.user.domain.vo.Recipient
import java.time.Instant

class ShippingAddress private constructor(
    val id: Long?,
    val userId: Long,
    private var _alias: String,
    private var _address: Address,
    private var _coordinates: Coordinates,
    private var _recipient: Recipient,
    private var _entranceInfo: String?,
    private var _areaCode: String,
    private var _default: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val alias: String get() = _alias
    val address: Address get() = _address
    val coordinates: Coordinates get() = _coordinates
    val recipient: Recipient get() = _recipient
    val recipientName: String get() = _recipient.name
    val recipientPhone: String get() = _recipient.phone
    val entranceInfo: String? get() = _entranceInfo
    val areaCode: String get() = _areaCode
    val isDefault: Boolean get() = _default

    fun update(
        alias: String,
        address: Address,
        coordinates: Coordinates,
        recipient: Recipient,
        entranceInfo: String?,
        areaCode: String,
    ) {
        requireInput(alias.isNotBlank()) { "배송지 별칭은 비어있을 수 없습니다" }
        requireInput(areaCode.isNotBlank()) { "지역 코드는 비어있을 수 없습니다" }
        _alias = alias
        _address = address
        _coordinates = coordinates
        _recipient = recipient
        _entranceInfo = entranceInfo
        _areaCode = areaCode
    }

    fun markAsDefault() {
        _default = true
    }

    fun unmarkAsDefault() {
        checkState(_default) { "기본 배송지가 아닌 배송지의 기본 설정을 해제할 수 없습니다" }
        _default = false
    }

    companion object {
        const val MAX_ADDRESSES_PER_USER = 10

        fun create(
            userId: Long,
            alias: String,
            address: Address,
            coordinates: Coordinates,
            recipient: Recipient,
            entranceInfo: String? = null,
            areaCode: String,
            isDefault: Boolean = false,
        ): ShippingAddress {
            requireInput(alias.isNotBlank()) { "배송지 별칭은 비어있을 수 없습니다" }
            requireInput(areaCode.isNotBlank()) { "지역 코드는 비어있을 수 없습니다" }
            return ShippingAddress(
                id = null,
                userId = userId,
                _alias = alias,
                _address = address,
                _coordinates = coordinates,
                _recipient = recipient,
                _entranceInfo = entranceInfo,
                _areaCode = areaCode,
                _default = isDefault,
                createdAt = null,
                updatedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            userId: Long,
            alias: String,
            address: Address,
            coordinates: Coordinates,
            recipient: Recipient,
            entranceInfo: String?,
            areaCode: String,
            isDefault: Boolean,
            createdAt: Instant,
            updatedAt: Instant,
        ): ShippingAddress = ShippingAddress(
            id = id,
            userId = userId,
            _alias = alias,
            _address = address,
            _coordinates = coordinates,
            _recipient = recipient,
            _entranceInfo = entranceInfo,
            _areaCode = areaCode,
            _default = isDefault,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
