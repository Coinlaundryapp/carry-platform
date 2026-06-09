package com.carry.order.application.service

import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderStatus
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * 재결제 시한이 지난 PAYMENT_FAILED 주문을 종결(취소)하는 안전망 스위퍼.
 *
 * 결제 실패 시 주문을 즉시 버리지 않고 재결제 창을 주지만(코인세탁은 결제가 픽업 이후),
 * 무한정 PAYMENT_FAILED 로 방치하면 좀비 주문이 된다. 시한 초과 시 SYSTEM 취소로 종결한다.
 *
 * 멀티 인스턴스 환경에선 @SchedulerLock 으로 매 주기 한 노드만 실행한다(ShedLock).
 * 락이 풀려 중복 실행되더라도 cancelOrder 의 상태 가드로 멱등하다.
 */
@Component
class PaymentRetryDeadlineSweeper(
    private val orderPersistencePort: OrderPersistencePort,
    private val orderCommandUseCase: OrderCommandUseCase,
    private val clock: Clock,
    @Value("\${carry.order.payment-retry-deadline-hours:24}") private val deadlineHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.order.payment-retry-sweep-interval-ms:3600000}")
    @SchedulerLock(name = "paymentRetryDeadlineSweep", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    fun sweepExpiredPaymentFailedOrders() {
        val cutoff = clock.instant().minus(deadlineHours, ChronoUnit.HOURS)
        val expired = orderPersistencePort.findByStatusAndUpdatedAtBefore(OrderStatus.PAYMENT_FAILED, cutoff)
        if (expired.isEmpty()) return

        log.info("재결제 시한 스위퍼: 시한 초과 PAYMENT_FAILED 주문 {}건 종결 시작", expired.size)
        expired.forEach { order ->
            try {
                orderCommandUseCase.cancelOrder(order.id!!, "재결제 시한 초과", CancelledBy.SYSTEM.name)
            } catch (e: Exception) {
                // 멀티 인스턴스 레이스 등으로 이미 종결된 주문은 건너뛰고 배치를 계속한다.
                log.warn("재결제 시한 스위퍼: 주문 {} 종결 실패(이미 종결됐을 수 있음) — {}", order.id, e.message)
            }
        }
    }
}
