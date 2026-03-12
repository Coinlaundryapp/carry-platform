package com.carry.payment.application.port.outbound

import com.carry.payment.domain.vo.PgProvider
import org.springframework.stereotype.Component

interface PgProviderAdapter : PaymentGatewayPort {
    fun supports(): PgProvider
}

@Component
class PgProviderRegistry(
    adapters: List<PgProviderAdapter>,
) {
    private val adapterMap: Map<PgProvider, PgProviderAdapter> =
        adapters.associateBy { it.supports() }

    fun resolve(provider: PgProvider): PaymentGatewayPort {
        return adapterMap[provider]
            ?: throw IllegalArgumentException("지원하지 않는 PG사입니다: $provider")
    }
}
