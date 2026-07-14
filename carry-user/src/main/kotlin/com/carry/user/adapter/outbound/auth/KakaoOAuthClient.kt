package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.domain.exception.OAuthProviderUnavailableException
import com.carry.user.domain.exception.OAuthTokenInvalidException
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Kakao access token을 `GET /v2/user/me`로 검증해 프로필을 얻는 어댑터.
 * client secret 불요(user/me는 베어러 토큰만). RestClient는 KakaoClientConfig에서 base-url로 구성.
 */
class KakaoOAuthClient(
    private val restClient: RestClient,
) : OAuthProfileClient {

    override fun fetchKakaoProfile(accessToken: String): OAuthProfile {
        val response = try {
            restClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(KakaoUserResponse::class.java)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 401) throw OAuthTokenInvalidException()
            throw OAuthProviderUnavailableException(e)
        } catch (e: RestClientException) {
            // 연결 실패·타임아웃·역직렬화 오류 등
            throw OAuthProviderUnavailableException(e)
        }

        val oauthId = response?.id ?: throw OAuthTokenInvalidException()
        return OAuthProfile(
            oauthId = oauthId.toString(),
            email = response.kakaoAccount?.email,
            nickname = response.kakaoAccount?.profile?.nickname,
            emailVerified = response.kakaoAccount?.isEmailVerified ?: false,
        )
    }

    data class KakaoUserResponse(
        val id: Long? = null,
        @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount? = null,
    )

    data class KakaoAccount(
        val email: String? = null,
        val profile: KakaoProfile? = null,
        @JsonProperty("is_email_verified") val isEmailVerified: Boolean? = null,
    )

    data class KakaoProfile(
        val nickname: String? = null,
    )
}
