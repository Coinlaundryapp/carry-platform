package com.carry.security.jwt

/**
 * REFRESH 토큰에서 복원한 클레임. 회전/폐기를 위해 세션 정체성(sessionId)과
 * 토큰 정체성(jti)을 함께 담는다.
 *
 * - [sessionId]: 한 디바이스 로그인 동안 회전돼도 불변. allowlist 키의 기반.
 * - [jti]: 토큰마다 고유. 회전마다 갱신되며 "현재 유효 jti"와의 일치로 재사용을 판별한다.
 */
data class RefreshClaims(
    val userId: Long,
    val sessionId: String,
    val jti: String,
)
