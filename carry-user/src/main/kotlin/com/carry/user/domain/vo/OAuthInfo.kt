package com.carry.user.domain.vo

data class OAuthInfo(
    val provider: OAuthProvider,
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "OAuth ID는 비어있을 수 없습니다" }
    }
}

enum class OAuthProvider {
    KAKAO
}
