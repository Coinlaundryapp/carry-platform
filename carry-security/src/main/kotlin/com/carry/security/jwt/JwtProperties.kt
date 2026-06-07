package com.carry.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpiration: Long = 3600000,
    val refreshTokenExpiration: Long = 604800000,
    // 2-step 가입 1단계 토큰 수명(짧게). 기본 10분.
    val signupTokenExpiration: Long = 600000,
    // refresh 회전 유예창(ms). 직전 jti를 이 시간 내 재시도는 정상으로 관용(네트워크 재시도·더블탭).
    // 초과 시 옛 토큰 재사용 = 탈취로 간주해 세션 폐기. 기본 10초.
    val refreshTokenRotationGraceMillis: Long = 10000,
)
