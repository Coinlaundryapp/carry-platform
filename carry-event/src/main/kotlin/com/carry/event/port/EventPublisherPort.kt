package com.carry.event.port

/**
 * 도메인 애플리케이션 서비스가 Outbox 인프라를 직접 알지 않도록 분리하는 아웃바운드 포트.
 *
 * 구현체는 인프라 어셈블리(carry-infra-kafka)에 위치한다.
 */
interface EventPublisherPort {
    fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        traceId: String? = null,
    )
}
