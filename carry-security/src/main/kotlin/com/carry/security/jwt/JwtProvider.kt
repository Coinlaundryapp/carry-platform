package com.carry.security.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
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
            .withClaim(CLAIM_PURPOSE, PURPOSE_ACCESS)
            .withClaim(CLAIM_ROLE, role)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtProperties.accessTokenExpiration))
            .sign(algorithm)
    }

    fun createRefreshToken(userId: Long): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim(CLAIM_PURPOSE, PURPOSE_REFRESH)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtProperties.refreshTokenExpiration))
            .sign(algorithm)
    }

    /**
     * 2-step 가입의 1단계 토큰. 서버가 검증한 OAuth 신원을 담되 유저(userId)는 아직 없다.
     * subject = oauthId. 탈취 시 임의 프로필로 가입 시도가 가능하므로 수명을 짧게 둔다.
     */
    fun createSignupToken(provider: String, oauthId: String, email: String?, nickname: String?): String {
        val builder = JWT.create()
            .withSubject(oauthId)
            .withClaim(CLAIM_PURPOSE, PURPOSE_SIGNUP)
            .withClaim(CLAIM_PROVIDER, provider)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + jwtProperties.signupTokenExpiration))
        if (email != null) builder.withClaim(CLAIM_EMAIL, email)
        if (nickname != null) builder.withClaim(CLAIM_NICKNAME, nickname)
        return builder.sign(algorithm)
    }

    /**
     * 인증 필터 경로. **purpose=ACCESS 토큰만** 인증으로 수용한다.
     * (REFRESH/SIGNUP 토큰을 베어러로 제시해도 거부 → 토큰 교차 악용 차단.)
     */
    fun parseToken(token: String): JwtPrincipal? {
        val decoded = verify(token, PURPOSE_ACCESS) ?: return null
        val userId = decoded.subject?.toLongOrNull() ?: return null
        val role = decoded.getClaim(CLAIM_ROLE).asString() ?: DEFAULT_ROLE
        return JwtPrincipal(userId, role)
    }

    /** purpose=REFRESH 토큰만 수용. userId 반환. */
    fun parseRefreshToken(token: String): Long? {
        val decoded = verify(token, PURPOSE_REFRESH) ?: return null
        return decoded.subject?.toLongOrNull()
    }

    /** purpose=SIGNUP 토큰만 수용. OAuth 신원 복원. */
    fun parseSignupToken(token: String): SignupClaims? {
        val decoded = verify(token, PURPOSE_SIGNUP) ?: return null
        val provider = decoded.getClaim(CLAIM_PROVIDER).asString() ?: return null
        val oauthId = decoded.subject ?: return null
        return SignupClaims(
            provider = provider,
            oauthId = oauthId,
            email = decoded.getClaim(CLAIM_EMAIL).asString(),
            nickname = decoded.getClaim(CLAIM_NICKNAME).asString(),
        )
    }

    private fun verify(token: String, expectedPurpose: String): DecodedJWT? {
        return try {
            val decoded = JWT.require(algorithm).build().verify(token)
            if (decoded.getClaim(CLAIM_PURPOSE).asString() != expectedPurpose) null else decoded
        } catch (e: JWTVerificationException) {
            null
        }
    }

    companion object {
        private const val DEFAULT_ROLE = "CUSTOMER"
        private const val CLAIM_PURPOSE = "purpose"
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_PROVIDER = "provider"
        private const val CLAIM_EMAIL = "email"
        private const val CLAIM_NICKNAME = "nickname"
        private const val PURPOSE_ACCESS = "ACCESS"
        private const val PURPOSE_REFRESH = "REFRESH"
        private const val PURPOSE_SIGNUP = "SIGNUP"
    }
}
