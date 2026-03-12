package com.carry.user.domain.model

import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import java.time.Instant

class ShippingAddress private constructor(
    val id: Long?,
    val userId: Long,
    private var _alias: String,
    private var _address: Address,
    private var _coordinates: Coordinates,
    private var _recipientName: String,
    private var _recipientPhone: String,
    private var _entranceInfo: String?,
    private var _areaCode: String,
    private var _default: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val alias: String get() = _alias
    val address: Address get() = _address
    val coordinates: Coordinates get() = _coordinates
    val recipientName: String get() = _recipientName
    val recipientPhone: String get() = _recipientPhone
    val entranceInfo: String? get() = _entranceInfo
    val areaCode: String get() = _areaCode
    val isDefault: Boolean get() = _default

    fun update(
        alias: String,
        address: Address,
        coordinates: Coordinates,
        recipientName: String,
        recipientPhone: String,
        entranceInfo: String?,
        areaCode: String,
    ) {
        require(alias.isNotBlank()) { "배송지 별칭은 비어있을 수 없습니다" }
        require(recipientName.isNotBlank()) { "수령인 이름은 비어있을 수 없습니다" }
        require(recipientPhone.isNotBlank()) { "수령인 전화번호는 비어있을 수 없습니다" }
        require(areaCode.isNotBlank()) { "지역 코드는 비어있을 수 없습니다" }
        _alias = alias
        _address = address
        _coordinates = coordinates
        _recipientName = recipientName
        _recipientPhone = recipientPhone
        _entranceInfo = entranceInfo
        _areaCode = areaCode
    }

    fun markAsDefault() {
        _default = true
    }

    fun unmarkAsDefault() {
        check(_default) { "기본 배송지가 아닌 배송지의 기본 설정을 해제할 수 없습니다" }
        _default = false
    }

    companion object {
        const val MAX_ADDRESSES_PER_USER = 10

        fun create(
            userId: Long,
            alias: String,
            address: Address,
            coordinates: Coordinates,
            recipientName: String,
            recipientPhone: String,
            entranceInfo: String? = null,
            areaCode: String,
            isDefault: Boolean = false,
        ): ShippingAddress {
            require(alias.isNotBlank()) { "배송지 별칭은 비어있을 수 없습니다" }
            require(recipientName.isNotBlank()) { "수령인 이름은 비어있을 수 없습니다" }
            require(recipientPhone.isNotBlank()) { "수령인 전화번호는 비어있을 수 없습니다" }
            require(areaCode.isNotBlank()) { "지역 코드는 비어있을 수 없습니다" }
            return ShippingAddress(
                id = null,
                userId = userId,
                _alias = alias,
                _address = address,
                _coordinates = coordinates,
                _recipientName = recipientName,
                _recipientPhone = recipientPhone,
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
            recipientName: String,
            recipientPhone: String,
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
            _recipientName = recipientName,
            _recipientPhone = recipientPhone,
            _entranceInfo = entranceInfo,
            _areaCode = areaCode,
            _default = isDefault,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
