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

    fun validateToken(token: String): Long? {
        return try {
            val verifier = JWT.require(algorithm).build()
            val decoded = verifier.verify(token)
            decoded.subject.toLongOrNull()
        } catch (e: JWTVerificationException) {
            null
        }
    }
}
