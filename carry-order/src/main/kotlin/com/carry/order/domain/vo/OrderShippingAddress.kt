package com.carry.order.domain.vo

import com.carry.common.exception.requireInput

data class OrderShippingAddress(
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String?,
    val latitude: Double,
    val longitude: Double,
    val recipientName: String,
    val recipientPhone: String,
    val entranceInfo: String?,
    val areaCode: String,
) {
    init {
        requireInput(roadAddress.isNotBlank()) { "도로명 주소는 필수입니다" }
        requireInput(recipientName.isNotBlank()) { "수령인 이름은 필수입니다" }
        requireInput(recipientPhone.isNotBlank()) { "수령인 전화번호는 필수입니다" }
        requireInput(areaCode.isNotBlank()) { "지역 코드는 필수입니다" }
    }
}
