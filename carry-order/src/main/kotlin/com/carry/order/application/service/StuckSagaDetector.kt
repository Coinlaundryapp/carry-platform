package com.carry.order.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.vo.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * 비종결 중간 상태에서 장기 정체된 주문(=진행이 멈춘 Choreography Saga)을 감지하는 안전망.
 *
 * 본 시스템의 사가 상태는 각 애그리거트에 분산 저장되고(ADR-0004), 재시작 복원력은 Outbox 내구성 +
 * Kafka 오프셋 재배달 + 멱등 소비(ADR-0002)로 이미 보장된다. 알려진 정체 원인은 전용 스위퍼가
 * 종결한다(배차 미수락 → DispatchTimeoutSweeper, 재결제 시한 → PaymentRetryDeadlineSweeper).
 *
 * 그럼에도 외부 트리거 부재 등으로 어떤 주문이 중간 상태에 머물 수 있다. 이 디텍터는 그런 정체를
 * `carry.saga.stuck` 메트릭 + 경고 로그로 **가시화**한다(비파괴 — 자동 취소/재발행은 상태별 비즈니스
 * 정책이라 범위 밖). 운영/알럿이 이를 보고 개입하는 진입점이다.
 */
@Component
class StuckSagaDetector(
    private val orderPersistencePort: OrderPersistencePort,
    private val metrics: MetricsPort,
    private val clock: Clock,
    @Value("\${carry.order.stuck-saga-threshold-hours:6}") private val thresholdHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.order.stuck-saga-scan-interval-ms:600000}")
    fun detectStuckSagas() {
        val cutoff = clock.instant().minus(thresholdHours, ChronoUnit.HOURS)
        WATCHED_STATUSES.forEach { status ->
            orderPersistencePort.findByStatusAndUpdatedAtBefore(status, cutoff).forEach { order ->
                metrics.incrementCounter("carry.saga.stuck", "status" to status.name)
                log.warn(
                    "정체 사가 감지: orderId={} status={} (마지막 갱신 이후 {}h 초과)",
                    order.id, status.name, thresholdHours,
                )
            }
        }
    }

    companion object {
        /**
         * 감시 대상 = 비종결 중간 상태. 종결(COMPLETED/CANCELLED/REFUNDED)과,
         * 전용 스위퍼가 종결을 책임지는 PAYMENT_FAILED 는 제외해 중복 경보를 피한다.
         */
        val WATCHED_STATUSES = listOf(
            OrderStatus.CREATED,
            OrderStatus.DISPATCHED,
            OrderStatus.PICKED_UP,
            OrderStatus.INVOICED,
            OrderStatus.PAID,
            OrderStatus.IN_PROGRESS,
            OrderStatus.REFUND_PENDING,
        )
    }
}
