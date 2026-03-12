package com.carry.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpiration: Long = 3600000,
    val refreshTokenExpiration: Long = 604800000
)
