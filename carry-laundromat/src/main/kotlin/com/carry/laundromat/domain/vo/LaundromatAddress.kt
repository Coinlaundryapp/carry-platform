package com.carry.laundromat.domain.vo

data class LaundromatAddress(
    val roadAddress: String,
    val detailAddress: String? = null,
    val zipCode: String? = null,
) {
    init {
        require(roadAddress.isNotBlank()) { "도로명 주소는 비어있을 수 없습니다" }
    }
}
