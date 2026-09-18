package com.carry.payment.application.port.outbound

import com.carry.payment.domain.vo.PgProvider

/**
 * PG provider 키로 [PaymentGatewayPort]를 가져오는 outbound port.
 *
 * 구현(`PgProviderRegistry`)은 adapter 계층에 위치하며 Circuit Breaker 같은 인프라
 * 데코레이션을 자유롭게 끼울 수 있다. application service는 데코레이션 사실을
 * 알 필요 없이 이 port만 사용한다.
 */
interface PaymentGatewayResolver {
    fun resolve(provider: PgProvider): PaymentGatewayPort

    /** 어댑터가 등록된 provider 목록 — 대사 잡이 등록분만 순회한다(미등록 provider resolve 는 throw). */
    fun supportedProviders(): Set<PgProvider>
}
