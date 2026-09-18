package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * 발행(ISSUED) 후 [thresholdHours] 경과했는데도 결제가 완료되지 않은 인보이스를 OVERDUE 로 확정하는 스위퍼.
 *
 * ChargeRetrySweeper 의 백오프 재시도가 소진되지 않은 채로도 연체 임계를 넘길 수 있으므로(신규 주문 차단이
 * 목적), 재시도 소진 여부와 무관하게 시간 기준으로 확정한다. ChargeRetrySweeper 와 별도 @SchedulerLock 으로
 * 병행 실행될 수 있어, 동시에 결제가 완료(PAID)된 인보이스를 덮어쓰지 않도록
 * [InvoicePersistencePort.markOverdueIfIssued] 조건부 UPDATE 로 lost-update 를 방지한다.
 */
@Component
class OverdueSweeper(
    private val invoicePersistencePort: InvoicePersistencePort,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
    @Value("\${carry.payment.overdue-threshold-hours:72}") private val thresholdHours: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.payment.overdue-sweep-interval-ms:3600000}")
    @SchedulerLock(name = "overdueSweep", lockAtMostFor = "PT50M", lockAtLeastFor = "PT0S")
    fun markOverdueInvoices() {
        val now = clock.instant()
        val cutoff = now.minus(Duration.ofHours(thresholdHours))
        invoicePersistencePort.findIssuedBefore(cutoff).forEach { invoice ->
            try {
                // 전이 허용 여부는 도메인이 판정한다 — 전이표(InvoiceStatus)와 조건부 UPDATE 의
                // ISSUED 가드가 어긋나면 여기서 먼저 막힌다. 실제 확정은 lost-update 를 막기 위해
                // 아래 조건부 UPDATE 가 하고, 이 호출의 상태 변경은 저장하지 않는다.
                invoice.markOverdue()
                if (invoicePersistencePort.markOverdueIfIssued(invoice.id!!, now)) {
                    log.warn("OverdueSweeper: 인보이스 연체 확정 invoiceId={} customerId={}", invoice.id, invoice.customerId)
                    metricsPort.incrementCounter("carry.payment.invoice_overdue")
                }
                // false: 조회 후 이미 PAID 등으로 바뀐 경우 — 조용히 skip(레이스, 정상 동작)
            } catch (e: Exception) {
                // 개별 건 실패가 배치 전체를 막지 않도록 — Refund/ChargeRetrySweeper 와 동일 원칙
                log.warn("OverdueSweeper: 연체 마킹 실패 invoiceId={}", invoice.id, e)
            }
        }
    }
}
