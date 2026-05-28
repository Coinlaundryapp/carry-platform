package com.carry.payment.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.domain.vo.PgProvider
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.stereotype.Component

/**
 * [PaymentGatewayResolver]의 production 구현.
 *
 * provider 키로 어댑터를 찾은 뒤 provider별 Circuit Breaker
 * (`pg-gateway-<provider>` 이름, `pg-gateway` config 사용)로 감싼 게이트웨이를 반환한다.
 *
 * 호출 측은 데코레이션 사실을 모르고 [PaymentGatewayPort]만 사용하므로 헥사고날 의존
 * 방향이 깨지지 않는다.
 */
@Component
class PgProviderRegistry(
    adapters: List<PgProviderAdapter>,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) : PaymentGatewayResolver {

    private val adapterMap: Map<PgProvider, PgProviderAdapter> =
        adapters.associateBy { it.supports() }

    override fun resolve(provider: PgProvider): PaymentGatewayPort {
        val raw = adapterMap[provider]
            ?: throw BusinessException(ErrorCode.UNSUPPORTED_PG_PROVIDER, "지원하지 않는 PG사입니다: $provider")
        val cb = circuitBreakerRegistry.circuitBreaker("pg-gateway-${provider.name.lowercase()}", "pg-gateway")
        return CircuitBreakerPaymentGateway(raw, cb)
    }
}
