package com.carry.order.domain.vo

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
        require(roadAddress.isNotBlank()) { "도로명 주소는 필수입니다" }
        require(recipientName.isNotBlank()) { "수령인 이름은 필수입니다" }
        require(recipientPhone.isNotBlank()) { "수령인 전화번호는 필수입니다" }
        require(areaCode.isNotBlank()) { "지역 코드는 필수입니다" }
    }
}
