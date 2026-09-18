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
    val emailVerified: Boolean,
)

/**
 * 발급된 REFRESH 토큰 + 회전/폐기에 필요한 식별자.
 * - [sessionId]: 한 디바이스 세션의 정체성(회전돼도 불변).
 * - [jti]: 이 토큰의 정체성(회전마다 갱신). allowlist에 "현재 유효 jti"로 저장된다.
 */
data class IssuedRefreshToken(
    val token: String,
    val sessionId: String,
    val jti: String,
)

/**
 * REFRESH 토큰에서 복원한 클레임.
 */
data class RefreshTokenClaims(
    val userId: Long,
    val sessionId: String,
    val jti: String,
)

/**
 * 토큰 발급/검증 아웃바운드 포트. 앱 레이어를 JWT 구현(carry-security)에서 격리한다.
 * (프레임워크/시큐리티 타입 비노출 — 어댑터에서만 JwtProvider 사용.)
 */
interface AuthTokenPort {
    fun issueAccessToken(userId: Long, role: com.carry.user.domain.vo.UserRole): String

    /** 새 세션 시작. sessionId·jti를 새로 생성해 refresh 토큰을 발급한다. */
    fun issueRefreshToken(userId: Long): IssuedRefreshToken

    /** 회전. 기존 [sessionId]를 유지하고 jti만 새로 생성해 refresh 토큰을 발급한다. */
    fun issueRefreshToken(userId: Long, sessionId: String): IssuedRefreshToken

    fun issueSignupToken(
        provider: OAuthProvider,
        oauthId: String,
        email: String?,
        nickname: String?,
        emailVerified: Boolean = false,
    ): String

    /** purpose=REFRESH 토큰만 수용. userId·sessionId·jti 복원, 그 외 null. */
    fun parseRefreshToken(token: String): RefreshTokenClaims?

    /** purpose=SIGNUP 토큰만 수용. 신원 복원, 그 외 null. */
    fun parseSignupToken(token: String): SignupIdentity?
}
