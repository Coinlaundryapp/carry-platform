package com.carry.user.application.port.outbound

import com.carry.user.domain.vo.OAuthProvider

/**
 * provider별 [OAuthProfileClient]를 조회하는 아웃바운드 포트.
 * provider마다 [OAuthProfileClient] 빈이 여러 개 존재하므로(Kakao/Naver/Google),
 * AuthService가 단일 빈 주입 모호성(NoUniqueBeanDefinitionException) 없이 provider로 라우팅하기 위해 사용한다.
 * 구현은 `adapter.outbound.auth.OAuthProfileClientResolver`(스프링이 `List<OAuthProfileClient>`를 주입).
 */
interface OAuthProfileResolver {
    fun resolve(provider: OAuthProvider): OAuthProfileClient
}
