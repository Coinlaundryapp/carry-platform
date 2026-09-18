package com.carry.user.application.port.outbound

import com.carry.user.domain.vo.OAuthProvider

/**
 * OAuth 제공자가 검증한 사용자 프로필. provider는 호출한 클라이언트가 결정.
 * email/nickname은 동의·스코프에 따라 없을 수 있다.
 */
data class OAuthProfile(
    val oauthId: String,
    val email: String?,
    val nickname: String?,
    val emailVerified: Boolean = false,
)

/**
 * OAuth 제공자에게 access token을 검증시켜 신뢰 가능한 프로필을 얻는 아웃바운드 포트.
 * (프레임워크 타입 비노출 — 어댑터에서만 RestClient 등 사용.)
 */
interface OAuthProfileClient {
    /** 이 클라이언트가 처리하는 provider. [OAuthProfileClientResolver]가 List 주입 후 매핑에 사용. */
    fun supports(): OAuthProvider

    fun fetchProfile(accessToken: String): OAuthProfile
}
