package com.carry.user.application.port.outbound

import com.carry.user.domain.vo.OAuthProvider

/**
 * SIGNUP 토큰에서 복원한 OAuth 신원(+가입 폼 prefill). 아직 유저는 없다.
 */
data class SignupIdentity(
    val provider: OAuthProvider,
    val oauthId: String,
    val email: String?,
    val nickname: String?,
)

/**
 * 토큰 발급/검증 아웃바운드 포트. 앱 레이어를 JWT 구현(carry-security)에서 격리한다.
 * (프레임워크/시큐리티 타입 비노출 — 어댑터에서만 JwtProvider 사용.)
 */
interface AuthTokenPort {
    fun issueAccessToken(userId: Long, role: com.carry.user.domain.vo.UserRole): String
    fun issueRefreshToken(userId: Long): String
    fun issueSignupToken(provider: OAuthProvider, oauthId: String, email: String?, nickname: String?): String

    /** purpose=REFRESH 토큰만 수용. userId 반환, 그 외 null. */
    fun parseRefreshToken(token: String): Long?

    /** purpose=SIGNUP 토큰만 수용. 신원 복원, 그 외 null. */
    fun parseSignupToken(token: String): SignupIdentity?
}
