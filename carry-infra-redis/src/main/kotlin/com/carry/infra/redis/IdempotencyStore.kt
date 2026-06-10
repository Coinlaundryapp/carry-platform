package com.carry.infra.redis

/**
 * 명령측 멱등성 저장 메커니즘. 클라이언트 공급 키를 선점(reserve)하고, 처리 결과 id를 저장(complete)해
 * 동일 키 재요청을 재생(findCompletedId)한다. 모듈/도메인 무관 — 키 프리픽스로 키공간을 분리한다.
 *
 * 분산 구현은 [RedisIdempotencyStore], 비분산 fallback은 [InMemoryIdempotencyStore].
 */
interface IdempotencyStore {
    /** 키를 PENDING으로 선점한다. 처음 1회만 true(원자적). */
    fun reserve(key: String): Boolean

    /** 완료된 키의 결과 id. PENDING(진행 중)이거나 부재면 null. */
    fun findCompletedId(key: String): Long?

    /** 처리 결과 id를 저장한다(이후 동일 키는 재생). */
    fun complete(key: String, id: Long)
}
