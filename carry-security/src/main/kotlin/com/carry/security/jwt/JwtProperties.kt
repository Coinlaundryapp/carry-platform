package com.carry.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpiration: Long = 3600000,
    val refreshTokenExpiration: Long = 604800000,
    // 2-step 가입 1단계 토큰 수명(짧게). 기본 10분.
    val signupTokenExpiration: Long = 600000
)
