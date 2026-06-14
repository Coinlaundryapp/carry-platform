package com.carry.payment.adapter.outbound.stub

import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.domain.vo.PgProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 로컬/E2E 전용 스텁 PG 어댑터.
 *
 * 실 PG 어댑터([PgProvider.TOSS_PAYMENTS])의 production 빈이 아직 없어
 * [com.carry.payment.adapter.outbound.resilience.PgProviderRegistry]의 라우팅 맵이 비고,
 * `resolve()`가 `UNSUPPORTED_PG_PROVIDER`를 던져 주문이 PAID에 도달하지 못한다.
 * 이 어댑터는 `local` 프로파일에서만 활성화되어 결제·환불을 결정적으로 성공시킨다 →
 * 로컬/E2E에서 결제 완료·배달 완주·환불 보상 사가를 끝까지 관통할 수 있다.
 *
 * 운영 프로파일에는 등록되지 않으므로 실제 결제 경로에는 영향이 없다.
 */
@Component
@Profile("local")
class StubPgProviderAdapter : PgProviderAdapter {

    override fun supports(): PgProvider = PgProvider.TOSS_PAYMENTS

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResult =
        PgPaymentResult(
            success = true,
            // 동일 결제키 → 동일 거래 ID (재시도 멱등 재생과 정합).
            pgTransactionId = "STUB-${request.orderId}-${request.paymentKey}",
        )

    override fun cancelPayment(pgTransactionId: String): PgCancelResult =
        PgCancelResult(success = true)
}
