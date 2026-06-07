package com.carry.user.application.port.outbound

/**
 * OAuth 제공자가 검증한 사용자 프로필. provider는 호출 메서드가 결정(현재 KAKAO).
 * email/nickname은 동의·스코프에 따라 없을 수 있다.
 */
data class OAuthProfile(
    val oauthId: String,
    val email: String?,
    val nickname: String?,
)

/**
 * OAuth 제공자에게 access token을 검증시켜 신뢰 가능한 프로필을 얻는 아웃바운드 포트.
 * (프레임워크 타입 비노출 — 어댑터에서만 RestClient 등 사용.)
 */
interface OAuthProfileClient {
    fun fetchKakaoProfile(accessToken: String): OAuthProfile
}
