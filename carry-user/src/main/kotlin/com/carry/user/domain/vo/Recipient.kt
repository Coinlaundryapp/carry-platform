package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

/**
 * 배송지 수령인(이름·전화번호). 이름/전화번호는 함께 채워지는 응집 개념이며
 * 비어있을 수 없다는 불변을 init에서 중앙 검증한다.
 */
data class Recipient(
    val name: String,
    val phone: String,
) {
    init {
        requireInput(name.isNotBlank()) { "수령인 이름은 비어있을 수 없습니다" }
        requireInput(phone.isNotBlank()) { "수령인 전화번호는 비어있을 수 없습니다" }
    }
}
