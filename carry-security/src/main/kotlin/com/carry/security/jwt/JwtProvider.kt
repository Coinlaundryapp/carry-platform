package com.carry.security.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import org.springframework.stereotype.Component
import java.util.Date

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties
) {
    private val algorithm: Algorithm by lazy {
        Algorithm.HMAC256(jwtProperties.secret)
    }

    fun createAccessToken(userId: Long, role: String): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("role", role)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtProperties.accessTokenExpiration))
            .sign(algorithm)
    }

    fun createRefreshToken(userId: Long): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtProperties.refreshTokenExpiration))
            .sign(algorithm)
    }

    /**
     * 토큰을 검증하고 인증 주체(userId)와 role을 함께 추출한다.
     * role claim이 없는 토큰(예: 리프레시 토큰)은 최소 권한인 CUSTOMER로 간주한다.
     * 검증 실패(위조·만료)면 null.
     */
    fun parseToken(token: String): JwtPrincipal? {
        return try {
            val decoded = JWT.require(algorithm).build().verify(token)
            val userId = decoded.subject?.toLongOrNull() ?: return null
            val role = decoded.getClaim("role").asString() ?: DEFAULT_ROLE
            JwtPrincipal(userId, role)
        } catch (e: JWTVerificationException) {
            null
        }
    }

    companion object {
        private const val DEFAULT_ROLE = "CUSTOMER"
    }
}
