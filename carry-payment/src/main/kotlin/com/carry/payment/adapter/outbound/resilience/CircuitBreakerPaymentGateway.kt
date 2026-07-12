package com.carry.payment.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PgBillingChargeRequest
import com.carry.payment.application.port.outbound.PgBillingKeyRequest
import com.carry.payment.application.port.outbound.PgBillingKeyResult
import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.application.port.outbound.PgTransactionRecord
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.time.Instant

/**
 * [PaymentGatewayPort] 데코레이터 — 외부 PG 호출을 Resilience4j Circuit Breaker로 감싸
 * 장애 전파를 차단한다.
 *
 * - 정상 호출은 그대로 위임
 * - delegate가 던지는 예외는 그대로 전파(보호 대상이 아닌 도메인 흐름에서 의미 보존)
 * - Circuit이 OPEN 상태일 때는 [CallNotPermittedException]을 즉시 던지므로
 *   [BusinessException]([ErrorCode.PG_GATEWAY_UNAVAILABLE])로 변환해 호출 측에 명확한
 *   재시도 신호를 준다.
 */
class CircuitBreakerPaymentGateway(
    private val delegate: PaymentGatewayPort,
    private val circuitBreaker: CircuitBreaker,
) : PaymentGatewayPort {

    override fun issueBillingKey(request: PgBillingKeyRequest): PgBillingKeyResult =
        execute { delegate.issueBillingKey(request) }

    override fun chargeBilling(request: PgBillingChargeRequest): PgPaymentResult =
        execute { delegate.chargeBilling(request) }

    override fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult =
        execute { delegate.cancelPayment(pgTransactionId, idempotencyKey) }

    override fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord> =
        execute { delegate.listTransactions(from, to) }

    private fun <T> execute(block: () -> T): T =
        try {
            circuitBreaker.executeSupplier(block)
        } catch (e: CallNotPermittedException) {
            throw BusinessException(
                ErrorCode.PG_GATEWAY_UNAVAILABLE,
                "${circuitBreaker.name} circuit OPEN — ${e.message}",
                e,
            )
        }
}
