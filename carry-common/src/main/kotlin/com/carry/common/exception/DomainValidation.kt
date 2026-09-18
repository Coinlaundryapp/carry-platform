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
 * - [requireInput]    : 입력/값 검증 실패 → 400 INVALID_INPUT
 * - [checkState]      : 상태 전이/상태 기반 가드 실패 → 409 CONFLICT (기본), 필요 시 errorCode 지정
 * - [checkInvariant]  : **내부 일관성** 위반 → 500 INTERNAL_ERROR (클라이언트 잘못이 아니라 우리 버그)
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

/**
 * 내부 일관성 불변식 — 깨지면 **클라이언트가 아니라 우리 코드가 틀린 것**이므로 500 이 맞다.
 *
 * Kotlin 표준 [require] 도 결과적으로 500 이 되지만, 그건 catch-all 핸들러에 걸린 결과라
 * "4xx 로 매핑하는 걸 깜빡했다" 와 구분되지 않는다. 이 헬퍼를 쓰면 500 이 **의도**임이 코드에
 * 드러나고, [GlobalExceptionHandler] 가 에러 코드 이름과 함께 error 레벨로 남긴다.
 *
 * 예: 원장 거래 그룹의 Σ=0 균형 — 입력 검증을 통과한 뒤 산식이 어긋났다는 뜻이다.
 */
inline fun checkInvariant(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        throw BusinessException(ErrorCode.INTERNAL_ERROR, lazyMessage())
    }
}
