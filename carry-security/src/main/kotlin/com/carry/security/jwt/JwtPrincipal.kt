package com.carry.security.jwt

/**
 * JWT에서 추출한 인증 주체. principal은 userId로 유지하고 role은 인가에만 사용한다.
 */
data class JwtPrincipal(
    val userId: Long,
    val role: String,
)
