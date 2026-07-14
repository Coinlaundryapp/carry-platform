package com.carry.user.adapter.outbound.auth

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.domain.vo.OAuthProvider
import org.springframework.stereotype.Component

/**
 * provider별 [OAuthProfileClient] 빈을 `supports()` 기준으로 매핑해 조회하는 리졸버.
 * 스프링이 List<OAuthProfileClient>로 모든 클라이언트 빈을 주입한다.
 */
@Component
class OAuthProfileClientResolver(clients: List<OAuthProfileClient>) {

    private val byProvider = clients.associateBy { it.supports() }

    fun resolve(provider: OAuthProvider): OAuthProfileClient =
        byProvider[provider] ?: throw BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 provider: $provider")
}
