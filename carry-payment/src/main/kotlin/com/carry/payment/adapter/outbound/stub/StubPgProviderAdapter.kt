package com.carry.payment.adapter.outbound.stub

import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.port.outbound.PgTransactionRecord
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.domain.vo.PgProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 로컬/E2E 전용 스텁 PG 어댑터.
 *
 * 실 PG 어댑터([PgProvider.TOSS_PAYMENTS])의 production 빈이 아직 없어
 * [com.carry.payment.adapter.outbound.resilience.PgProviderRegistry]의 라우팅 맵이 비고,
 * `resolve()`가 `UNSUPPORTED_PG_PROVIDER`를 던져 주문이 PAID에 도달하지 못한다.
 * 이 어댑터는 `local` 프로파일에서만 활성화되어 결제·환불을 결정적으로 성공시킨다 →
 * 로컬/E2E에서 결제 완료·배달 완주·환불 보상 사가를 끝까지 관통할 수 있다.
 *
 * 대사(reconciliation) 잡을 위해 과금·취소를 인메모리 거래 로그로 기록하고
 * [listTransactions]로 재생한다 — "PG 측 원장"의 스텁. 재기동 시 소실되지만
 * 로컬 전용이라 허용(실 PG 도입 시 원장은 PG 가 보관).
 *
 * 운영 프로파일에는 등록되지 않으므로 실제 결제 경로에는 영향이 없다.
 */
@Component
@Profile("local")
class StubPgProviderAdapter(
    private val clock: Clock,
) : PgProviderAdapter {

    private val transactions = CopyOnWriteArrayList<PgTransactionRecord>()

    override fun supports(): PgProvider = PgProvider.TOSS_PAYMENTS

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResult {
        // 동일 결제키 → 동일 거래 ID (재시도 멱등 재생과 정합).
        val pgTransactionId = "STUB-${request.orderId}-${request.paymentKey}"
        // 멱등 재시도가 원장에 중복 CHARGE 로 남지 않도록 최초 1회만 기록.
        if (transactions.none { it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CHARGE }) {
            transactions += PgTransactionRecord(pgTransactionId, PgTransactionType.CHARGE, request.amount, clock.instant())
        }
        return PgPaymentResult(success = true, pgTransactionId = pgTransactionId)
    }

    // 스텁의 취소는 pgTransactionId 기준으로 이미 결정적(중복 CANCEL 미기록·동일 결과 재생)이라
    // 멱등키는 실 어댑터 헤더 전달용 자리만 차지한다.
    override fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult {
        val charge = transactions.firstOrNull { it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CHARGE }
        if (transactions.none { it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CANCEL }) {
            transactions += PgTransactionRecord(
                pgTransactionId, PgTransactionType.CANCEL, charge?.amount ?: 0L, clock.instant(),
            )
        }
        return PgCancelResult(success = true, refundAmount = charge?.amount)
    }

    override fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord> =
        transactions.filter { !it.occurredAt.isBefore(from) && !it.occurredAt.isAfter(to) }
}
