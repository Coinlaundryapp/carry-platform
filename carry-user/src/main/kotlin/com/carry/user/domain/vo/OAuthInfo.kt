package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

data class OAuthInfo(
    val provider: OAuthProvider,
    val id: String,
) {
    init {
        requireInput(id.isNotBlank()) { "OAuth ID는 비어있을 수 없습니다" }
    }
}

enum class OAuthProvider {
    KAKAO
}
