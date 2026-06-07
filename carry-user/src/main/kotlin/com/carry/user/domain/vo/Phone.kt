package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

@JvmInline
value class Phone(val value: String) {
    init {
        requireInput(value.isNotBlank()) { "전화번호는 비어있을 수 없습니다" }
        requireInput(value.matches(PATTERN)) { "올바르지 않은 전화번호 형식입니다: $value" }
    }

    companion object {
        private val PATTERN = Regex("^01[016789]-?\\d{3,4}-?\\d{4}$")
    }
}
