package com.carry.common.exception

/**
 * 도메인 불변식 검증 헬퍼.
 *
 * Kotlin 표준 [require]/[check]는 각각 `IllegalArgumentException`/`IllegalStateException`을 던지는데,
 * 이는 [GlobalExceptionHandler]의 catch-all 핸들러에 걸려 **HTTP 500**으로 응답된다.
 * 도메인 검증 실패는 클라이언트 잘못이므로 4xx여야 한다.
 *
 * 이 헬퍼들은 [require]/[check]와 동일한 사용감을 유지하되 [BusinessException]을 던져
 * 올바른 상태 코드로 매핑되게 한다.
 *
 * - [requireInput]  : 입력/값 검증 실패 → 400 INVALID_INPUT
 * - [checkState]    : 상태 전이/상태 기반 가드 실패 → 409 CONFLICT (기본), 필요 시 errorCode 지정
 */
inline fun requireInput(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        throw BusinessException(ErrorCode.INVALID_INPUT, lazyMessage())
    }
}

inline fun checkState(
    value: Boolean,
    errorCode: ErrorCode = ErrorCode.CONFLICT,
    lazyMessage: () -> String,
) {
    if (!value) {
        throw BusinessException(errorCode, lazyMessage())
    }
}
