package com.carry.order.application.port.outbound

/**
 * 명령(쓰기) 요청의 멱등성을 보장하기 위한 키-결과 저장 포트.
 *
 * 클라이언트가 보낸 `Idempotency-Key` 로 "같은 요청이 한 번만 수행"되게 한다.
 * 구현은 원자적 선점(SETNX)을 지원하는 저장소(Redis)에 둔다.
 */
interface IdempotencyPort {
    /**
     * 키를 원자적으로 선점한다(SETNX). 새로 선점하면 true, 이미 존재(진행 중/완료)하면 false.
     * 짧은 TTL의 PENDING 마커로 동시·즉시 재시도를 차단한다.
     */
    fun reserve(key: String): Boolean

    /**
     * 완료 저장된 결과(생성된 orderId)를 반환한다. 선점만 됐거나(진행 중) 키가 없으면 null.
     */
    fun findCompletedOrderId(key: String): Long?

    /**
     * 처리 완료 결과를 저장(긴 TTL로 교체)해 이후 동일 키 요청이 같은 결과로 재생되게 한다.
     */
    fun complete(key: String, orderId: Long)
}
