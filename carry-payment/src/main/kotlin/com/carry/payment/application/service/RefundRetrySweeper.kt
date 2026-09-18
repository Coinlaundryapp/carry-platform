package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.vo.PaymentStatus
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 환불 대기(REFUND_PENDING) 결제에 대해 PG 취소를 재시도하는 스위퍼.
 *
 * 주문 취소 시 [PaymentSagaHandler.onOrderCancelled] 는 결제를 REFUND_PENDING 으로만 표시하고
 * PG 를 호출하지 않는다(PG 장애와 무관하게 항상 성공 → DLQ 위험 제거). 실제 PG 환불은 본 스위퍼가
 * 주기적으로 수행한다. PG Circuit Breaker 가 OPEN 이거나 일시 실패해도 REFUND_PENDING 이 유지되어
 * 다음 주기에 자동 재시도되므로, "환불은 결국 성공한다"는 불변식을 보장한다.
 *
 * [PaymentCommandUseCase.executeRefund] 는 멱등(REFUND_PENDING 만 처리)하고, PG 취소는
 * pgTransactionId 기반이라 재시도-안전하다. 멀티 인스턴스에선 @SchedulerLock 으로 한 노드만 실행.
 */
@Component
class RefundRetrySweeper(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val paymentCommandUseCase: PaymentCommandUseCase,
    private val metrics: MetricsPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.payment.refund-retry-interval-ms:60000}")
    @SchedulerLock(name = "refundRetrySweep", lockAtMostFor = "PT50S", lockAtLeastFor = "PT0S")
    fun retryPendingRefunds() {
        val pending = paymentPersistencePort.findByStatus(PaymentStatus.REFUND_PENDING)
        if (pending.isEmpty()) return

        log.info("환불 재시도 스위퍼: REFUND_PENDING 결제 {}건 PG 환불 시도", pending.size)
        pending.forEach { payment ->
            try {
                paymentCommandUseCase.executeRefund(payment.orderId)
            } catch (e: Exception) {
                // PG CB OPEN/일시 실패 등 — REFUND_PENDING 유지하고 다음 주기에 재시도.
                metrics.incrementCounter("carry.payment.refund_retry_failed")
                log.warn("환불 재시도 실패(다음 주기 재시도) orderId={} — {}", payment.orderId, e.message)
            }
        }
    }
}
