package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.domain.exception.OAuthProviderUnavailableException
import com.carry.user.domain.exception.OAuthTokenInvalidException
import com.carry.user.domain.vo.OAuthProvider
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Naver access token을 `GET /v1/nid/me`로 검증해 프로필을 얻는 어댑터.
 * Naver는 이메일 인증 여부를 제공하지 않으므로 emailVerified는 항상 false로 고정한다.
 * RestClient는 NaverClientConfig에서 base-url로 구성.
 */
class NaverOAuthClient(
    private val restClient: RestClient,
) : OAuthProfileClient {

    override fun supports(): OAuthProvider = OAuthProvider.NAVER

    override fun fetchProfile(accessToken: String): OAuthProfile {
        val response = try {
            restClient.get()
                .uri("/v1/nid/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(NaverMeResponse::class.java)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 401) throw OAuthTokenInvalidException()
            throw OAuthProviderUnavailableException(e)
        } catch (e: RestClientException) {
            // 연결 실패·타임아웃·역직렬화 오류 등
            throw OAuthProviderUnavailableException(e)
        }

        val oauthId = response?.response?.id ?: throw OAuthTokenInvalidException()
        return OAuthProfile(
            oauthId = oauthId,
            email = response.response.email,
            nickname = response.response.name,
            emailVerified = false,
        )
    }

    data class NaverMeResponse(
        val resultcode: String? = null,
        val message: String? = null,
        val response: NaverProfileResponse? = null,
    )

    data class NaverProfileResponse(
        val id: String? = null,
        val email: String? = null,
        val name: String? = null,
    )
}
