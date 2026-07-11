package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgTransactionRecord
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.application.port.outbound.ReconciliationMismatchPort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.model.ReconciliationMismatch
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import com.carry.payment.domain.vo.ReconciliationMismatchType
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * PG 대사(reconciliation) 잡 — 앱 트랜잭션 아래의 안전망.
 *
 * PG 측 거래 원장([PaymentGatewayPort.listTransactions])을 주기적으로 pull 해
 * 로컬 `payment_payments` 와 양방향 diff 한다. 감지는 **비파괴** — 자동 보정 없이
 * `carry.payment.reconcile.mismatch` 메트릭 + 경고 로그 + 불일치 원장 적재만 수행해
 * 운영 알럿/수동 개입의 진입점이 된다(자동 수렴은 P2b 에서 정책화).
 *
 * 윈도 설계:
 * - PG-side  [now−lookback, now−grace] — grace(기본 10분)는 방금 승인돼 로컬 커밋이
 *   따라가는 중이거나 PG 목록 반영이 지연되는 인플라이트 거래의 오탐을 막는다.
 * - local-side [now−lookback+grace, now−grace] — PG 이벤트는 로컬 마킹보다 항상 먼저
 *   일어나므로, 로컬 윈도를 grace 만큼 안쪽으로 좁히면 경계의 PG 기록이 반드시
 *   PG-side 윈도 안에 있어 경계 오탐이 없다.
 * - 주기(기본 1h) 대비 lookback(기본 25h)이 커서 윈도가 중첩되지만,
 *   [ReconciliationMismatchPort.recordIfNew] 의 (type, dedupKey) 멱등이 중복을 흡수한다.
 */
@Component
class PgReconciliationJob(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val mismatchPort: ReconciliationMismatchPort,
    private val metrics: MetricsPort,
    private val clock: Clock,
    @Value("\${carry.payment.reconcile-lookback-ms:90000000}") private val lookbackMs: Long,
    @Value("\${carry.payment.reconcile-grace-ms:600000}") private val graceMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedRateString = "\${carry.payment.reconcile-interval-ms:3600000}",
        initialDelayString = "\${carry.payment.reconcile-initial-delay-ms:600000}",
    )
    @SchedulerLock(name = "pgReconciliation", lockAtMostFor = "PT50M", lockAtLeastFor = "PT0S")
    fun reconcile() {
        val now = clock.instant()
        val from = now.minusMillis(lookbackMs)
        val to = now.minusMillis(graceMs)
        if (!from.isBefore(to)) return

        paymentGatewayResolver.supportedProviders().forEach { provider ->
            val records = try {
                paymentGatewayResolver.resolve(provider).listTransactions(from, to)
            } catch (e: Exception) {
                // PG 장애/CB OPEN — 이번 주기는 skip, 다음 주기의 중첩 윈도가 재커버한다.
                metrics.incrementCounter("carry.payment.reconcile.pull_failed", "provider" to provider.name)
                log.warn("PG 대사: 거래 목록 조회 실패, 이번 주기 skip provider={} — {}", provider, e.message)
                return@forEach
            }
            reconcileProvider(provider, records, from, to)
        }
    }

    private fun reconcileProvider(provider: PgProvider, records: List<PgTransactionRecord>, from: Instant, to: Instant) {
        val charges = records.filter { it.type == PgTransactionType.CHARGE }.associateBy { it.pgTransactionId }
        val cancels = records.filter { it.type == PgTransactionType.CANCEL }.associateBy { it.pgTransactionId }

        // PG-side: PG 원장 기준으로 로컬 대조
        charges.values.forEach { charge ->
            val local = paymentPersistencePort.findByPgTransactionId(charge.pgTransactionId)
            when {
                local == null || local.status in setOf(PaymentStatus.PENDING, PaymentStatus.FAILED) ->
                    report(provider, ReconciliationMismatchType.ORPHAN_PG_CHARGE, local, charge.pgTransactionId, pgAmount = charge.amount,
                        detail = if (local == null) "PG 과금인데 로컬 결제 없음" else "PG 과금인데 로컬 결제가 ${local.status} 상태")

                local.amount != charge.amount ->
                    report(provider, ReconciliationMismatchType.AMOUNT_MISMATCH, local, charge.pgTransactionId, pgAmount = charge.amount,
                        detail = "과금 금액 불일치: 로컬 ${local.amount} vs PG ${charge.amount}")
            }
        }
        cancels.values.forEach { cancel ->
            val local = paymentPersistencePort.findByPgTransactionId(cancel.pgTransactionId)
            if (local == null || local.status != PaymentStatus.REFUNDED) {
                report(provider, ReconciliationMismatchType.PG_CANCEL_NOT_MARKED, local, cancel.pgTransactionId, pgAmount = cancel.amount,
                    detail = when {
                        local == null -> "PG 취소인데 로컬 결제 없음"
                        local.status == PaymentStatus.REFUND_PENDING ->
                            "PG 취소 완료인데 로컬 REFUND_PENDING — 스위퍼 PG 재호출 전 수렴 대상(P2b)"
                        else -> "PG 취소인데 로컬 결제가 ${local.status} 상태"
                    })
            }
        }

        // local-side: 로컬 원장 기준으로 PG 대조 (윈도는 grace 만큼 안쪽 — 경계 오탐 방지)
        val locals = paymentPersistencePort.findByProviderAndStatusInWindow(
            provider, listOf(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED), from.plusMillis(graceMs), to,
        )
        locals.forEach { payment ->
            val txId = payment.pgTransactionId
            when (payment.status) {
                PaymentStatus.COMPLETED ->
                    if (txId == null || txId !in charges) {
                        report(provider, ReconciliationMismatchType.MISSING_IN_PG, payment, txId, pgAmount = null,
                            detail = if (txId == null) "로컬 COMPLETED 인데 pgTransactionId 없음" else "로컬 COMPLETED 인데 PG 과금 기록 없음")
                    }

                PaymentStatus.REFUNDED ->
                    if (txId == null || txId !in cancels) {
                        report(provider, ReconciliationMismatchType.MISSING_PG_CANCEL, payment, txId, pgAmount = null,
                            detail = "로컬 REFUNDED 인데 PG 취소 기록 없음")
                    }

                else -> Unit
            }
        }
    }

    private fun report(
        provider: PgProvider,
        type: ReconciliationMismatchType,
        local: Payment?,
        pgTransactionId: String?,
        pgAmount: Long?,
        detail: String,
    ) {
        val mismatch = ReconciliationMismatch(
            type = type,
            dedupKey = pgTransactionId ?: "payment:${local?.id}",
            paymentId = local?.id,
            orderId = local?.orderId,
            pgTransactionId = pgTransactionId,
            localAmount = local?.amount,
            pgAmount = pgAmount,
            detail = detail,
        )
        // 이미 원장에 있는 불일치는 메트릭·로그도 억제 — 매 주기 중복 알럿 방지.
        if (mismatchPort.recordIfNew(mismatch)) {
            metrics.incrementCounter("carry.payment.reconcile.mismatch", "type" to type.name, "provider" to provider.name)
            log.warn("PG 대사 불일치 감지 [{}] provider={} paymentId={} orderId={} pgTxId={} — {}",
                type, provider, local?.id, local?.orderId, pgTransactionId, detail)
        }
    }
}
