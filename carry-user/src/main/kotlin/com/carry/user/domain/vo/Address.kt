package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

data class Address(
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
) {
    init {
        requireInput(roadAddress.isNotBlank()) { "도로명 주소는 비어있을 수 없습니다" }
        requireInput(zipCode.isNotBlank()) { "우편번호는 비어있을 수 없습니다" }
    }
}
