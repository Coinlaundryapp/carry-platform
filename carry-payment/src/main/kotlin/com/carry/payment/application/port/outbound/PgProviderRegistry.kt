package com.carry.payment.application.port.outbound

import com.carry.payment.domain.vo.PgProvider

/**
 * PG provider별 어댑터 식별자. 각 어댑터(adapter/outbound 패키지) 가 자신이 지원하는
 * provider를 [supports]로 노출하여 [PaymentGatewayResolver] 구현이 라우팅에 사용한다.
 */
interface PgProviderAdapter : PaymentGatewayPort {
    fun supports(): PgProvider
}
