package com.carry.security.jwt

/**
 * SIGNUP 토큰에서 복원한 OAuth 신원(+가입 폼 prefill). 유저(userId)는 아직 없다.
 */
data class SignupClaims(
    val provider: String,
    val oauthId: String,
    val email: String?,
    val nickname: String?,
    val emailVerified: Boolean,
)
