package com.carry.user.adapter.outbound.auth

import com.carry.security.jwt.JwtProvider
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.SignupIdentity
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.UserRole
import org.springframework.stereotype.Component

/**
 * AuthTokenPort 어댑터. carry-security의 JwtProvider에 위임하고
 * 도메인 타입(UserRole/OAuthProvider) ↔ 토큰 문자열 매핑을 담당한다.
 */
@Component
class JwtAuthTokenAdapter(
    private val jwtProvider: JwtProvider,
) : AuthTokenPort {

    override fun issueAccessToken(userId: Long, role: UserRole): String =
        jwtProvider.createAccessToken(userId, role.name)

    override fun issueRefreshToken(userId: Long): String =
        jwtProvider.createRefreshToken(userId)

    override fun issueSignupToken(provider: OAuthProvider, oauthId: String, email: String?, nickname: String?): String =
        jwtProvider.createSignupToken(provider.name, oauthId, email, nickname)

    override fun parseRefreshToken(token: String): Long? =
        jwtProvider.parseRefreshToken(token)

    override fun parseSignupToken(token: String): SignupIdentity? {
        val claims = jwtProvider.parseSignupToken(token) ?: return null
        val provider = runCatching { OAuthProvider.valueOf(claims.provider) }.getOrNull() ?: return null
        return SignupIdentity(provider, claims.oauthId, claims.email, claims.nickname)
    }
}
