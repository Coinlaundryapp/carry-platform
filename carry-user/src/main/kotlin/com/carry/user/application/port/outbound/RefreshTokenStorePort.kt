package com.carry.user.application.port.outbound

/**
 * [RefreshTokenStorePort.rotate] 결과.
 * - [ROTATED]: 정상 회전(또는 유예창 내 재시도). 새 토큰 발급 가능.
 * - [ABSENT]: 세션 없음(로그아웃됨/만료). 거부.
 * - [REUSE]: 이미 회전된 옛 토큰 재사용 = 탈취 신호. 세션은 폐기됨. 거부.
 */
enum class RotateResult { ROTATED, ABSENT, REUSE }

/**
 * refresh 토큰 회전/폐기 allowlist 아웃바운드 포트.
 *
 * 세션(sessionId)별로 "현재 유효한 jti" 하나를 보관한다. 탐지+회전+폐기는 어댑터 내부에서
 * 원자적으로 수행되며(경쟁 조건·TOCTOU 차단), 직전 jti는 짧은 유예창 동안 정상 재시도로 관용한다.
 * TTL·유예창 길이는 어댑터가 [com.carry.security.jwt.JwtProperties]에서 읽는다(단일 source-of-truth).
 */
interface RefreshTokenStorePort {

    /** 새 세션의 현재 jti를 기록한다(로그인/가입). */
    fun start(sessionId: String, jti: String)

    /**
     * [presentedJti]가 세션의 현재 jti면 [newJti]로 회전한다(원자적).
     * 직전 jti면 유예창 내에서 정상 재시도로 관용해 회전한다. 그 외(옛 토큰 재사용)면
     * 세션을 폐기하고 [RotateResult.REUSE]를 반환한다.
     */
    fun rotate(sessionId: String, presentedJti: String, newJti: String): RotateResult

    /** 세션을 폐기한다(로그아웃). 멱등 — 없는 세션 삭제는 no-op. */
    fun delete(sessionId: String)
}
