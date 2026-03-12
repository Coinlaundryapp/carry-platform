package com.carry.user.domain.vo

data class Address(
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
) {
    init {
        require(roadAddress.isNotBlank()) { "도로명 주소는 비어있을 수 없습니다" }
        require(zipCode.isNotBlank()) { "우편번호는 비어있을 수 없습니다" }
    }
}
