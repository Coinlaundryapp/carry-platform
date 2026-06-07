package com.carry.laundromat.domain.vo

import com.carry.common.exception.requireInput

data class LaundromatAddress(
    val roadAddress: String,
    val detailAddress: String? = null,
    val zipCode: String? = null,
) {
    init {
        requireInput(roadAddress.isNotBlank()) { "도로명 주소는 비어있을 수 없습니다" }
    }
}
