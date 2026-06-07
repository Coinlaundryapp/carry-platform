package com.carry.common.logging

import org.slf4j.MDC

/**
 * Saga 이벤트 핸들러가 처리 중인 사가의 상관관계 키(`orderId`)를 MDC에 넣어
 * 핸들러 내부에서 호출되는 모든 로그·예외 추적이 한 사가로 묶이게 한다.
 *
 * `traceId`/`spanId`는 Micrometer Tracing이 자동 관리하고, `saga.traceId`는
 * [com.carry.infra.kafka.consumer.EventConsumerSupport]가 이벤트 소비 진입 시
 * 채워 넣는다. 이 유틸은 **도메인 상관관계 키**(orderId)만 책임진다.
 *
 * ## 사용
 * ```kotlin
 * override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
 *     SagaLogContext.withOrderId(event.orderId) {
 *         log.info("Saga: onDispatchAccepted dispatchId={}", event.dispatchId)
 *         // ... 핸들러 본문
 *     }
 * }
 * ```
 *
 * ## 중첩 안전성
 * 호출 시점에 기존 `orderId`가 있으면 보존하고 블록 종료 시 복구한다.
 * 따라서 saga handler 안에서 다른 컴포넌트가 다시 호출돼도 누락 없이 동작한다.
 */
object SagaLogContext {

    const val KEY_ORDER_ID = "orderId"

    inline fun <T> withOrderId(orderId: Long, block: () -> T): T {
        val previous = MDC.get(KEY_ORDER_ID)
        MDC.put(KEY_ORDER_ID, orderId.toString())
        try {
            return block()
        } finally {
            if (previous != null) MDC.put(KEY_ORDER_ID, previous) else MDC.remove(KEY_ORDER_ID)
        }
    }
}
