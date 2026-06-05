package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

@JvmInline
value class Email(val value: String) {
    init {
        requireInput(value.isNotBlank()) { "이메일은 비어있을 수 없습니다" }
        requireInput(value.matches(PATTERN)) { "올바르지 않은 이메일 형식입니다: $value" }
    }

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
