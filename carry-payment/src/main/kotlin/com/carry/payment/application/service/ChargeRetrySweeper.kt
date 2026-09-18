package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * FAILED 결제 중 next_retry_at(백오프 예약 시각)이 도래한 건을 재과금하는 스위퍼.
 *
 * [AutoChargeService.attemptCharge]가 실패할 때마다 [com.carry.payment.domain.model.Payment.scheduleRetry]
 * 로 다음 재시도 시각(1h→4h→12h→24h→이후 24h 고정)을 예약해 두고, 본 스위퍼가 주기적으로 도래한 건만 골라
 * [AutoChargeService.retryCharge]를 호출한다. 멀티 인스턴스에선 @SchedulerLock 으로 한 노드만 실행.
 */
@Component
class ChargeRetrySweeper(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val autoChargeService: AutoChargeService,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.payment.charge-retry-interval-ms:600000}")
    @SchedulerLock(name = "chargeRetrySweep", lockAtMostFor = "PT9M", lockAtLeastFor = "PT0S")
    fun retryFailedCharges() {
        paymentPersistencePort.findRetryableFailed(clock.instant()).forEach { payment ->
            try {
                autoChargeService.retryCharge(payment.id!!)
            } catch (e: Exception) {
                // chargeBilling 예외는 AutoChargeService 내부에서 FAILED+백오프로 커밋되지만,
                // 그 밖의 예외(낙관락 충돌 등)로 여기 도달 시 개별 건 격리 — RefundRetrySweeper 동일 원칙.
                log.warn("ChargeRetrySweeper: 재과금 실패 paymentId={}", payment.id, e)
                metricsPort.incrementCounter("carry.payment.charge_retry_failed")
            }
        }
    }
}
