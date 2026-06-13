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
    KAKAO,

    /** 비프로덕션 dev-login 전용 합성 신원(예: `dev:customer`). prod에서는 발급 경로가 봉인된다. */
    DEV,
}
