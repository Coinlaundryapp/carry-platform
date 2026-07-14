package com.carry.user.adapter.outbound.auth

import com.carry.security.jwt.JwtProvider
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.IssuedRefreshToken
import com.carry.user.application.port.outbound.RefreshTokenClaims
import com.carry.user.application.port.outbound.SignupIdentity
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.UserRole
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * AuthTokenPort 어댑터. carry-security의 JwtProvider에 위임하고
 * 도메인 타입(UserRole/OAuthProvider) ↔ 토큰 문자열 매핑을 담당한다.
 *
 * sessionId/jti 생성(UUID)은 인프라 책임이므로 여기서 수행한다 — 발급된 토큰과
 * allowlist에 같은 jti가 쓰이도록 호출자(AuthService)가 [IssuedRefreshToken.jti]를 전달한다.
 */
@Component
class JwtAuthTokenAdapter(
    private val jwtProvider: JwtProvider,
) : AuthTokenPort {

    override fun issueAccessToken(userId: Long, role: UserRole): String =
        jwtProvider.createAccessToken(userId, role.name)

    override fun issueRefreshToken(userId: Long): IssuedRefreshToken =
        issueRefreshToken(userId, UUID.randomUUID().toString())

    override fun issueRefreshToken(userId: Long, sessionId: String): IssuedRefreshToken {
        val jti = UUID.randomUUID().toString()
        val token = jwtProvider.createRefreshToken(userId, sessionId, jti)
        return IssuedRefreshToken(token, sessionId, jti)
    }

    override fun issueSignupToken(
        provider: OAuthProvider,
        oauthId: String,
        email: String?,
        nickname: String?,
        emailVerified: Boolean,
    ): String =
        jwtProvider.createSignupToken(provider.name, oauthId, email, nickname, emailVerified)

    override fun parseRefreshToken(token: String): RefreshTokenClaims? {
        val claims = jwtProvider.parseRefreshToken(token) ?: return null
        return RefreshTokenClaims(claims.userId, claims.sessionId, claims.jti)
    }

    override fun parseSignupToken(token: String): SignupIdentity? {
        val claims = jwtProvider.parseSignupToken(token) ?: return null
        val provider = runCatching { OAuthProvider.valueOf(claims.provider) }.getOrNull() ?: return null
        return SignupIdentity(provider, claims.oauthId, claims.email, claims.nickname, claims.emailVerified)
    }
}
