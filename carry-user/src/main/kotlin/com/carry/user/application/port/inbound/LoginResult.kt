package com.carry.user.application.port.inbound

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/** 신규 가입 폼 prefill(있으면). */
data class Prefill(
    val email: String?,
    val nickname: String?,
)

/**
 * Kakao 로그인 결과. 기존 유저면 즉시 토큰, 신규면 가입 단계로.
 */
sealed interface LoginResult {
    data class Registered(val tokens: TokenPair) : LoginResult
    data class RegistrationRequired(val signupToken: String, val prefill: Prefill) : LoginResult
}
