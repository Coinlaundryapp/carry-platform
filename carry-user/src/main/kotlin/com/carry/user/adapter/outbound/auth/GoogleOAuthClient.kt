package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.domain.exception.OAuthProviderUnavailableException
import com.carry.user.domain.exception.OAuthTokenInvalidException
import com.carry.user.domain.vo.OAuthProvider
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Google access token을 `GET /v1/userinfo`로 검증해 프로필을 얻는 어댑터.
 * id_token 검증은 하지 않는다(access-token 기반 userinfo 조회만 사용).
 * RestClient는 GoogleClientConfig에서 base-url로 구성.
 */
class GoogleOAuthClient(
    private val restClient: RestClient,
) : OAuthProfileClient {

    override fun supports(): OAuthProvider = OAuthProvider.GOOGLE

    override fun fetchProfile(accessToken: String): OAuthProfile {
        val response = try {
            restClient.get()
                .uri("/v1/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(GoogleUserInfoResponse::class.java)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 401) throw OAuthTokenInvalidException()
            throw OAuthProviderUnavailableException(e)
        } catch (e: RestClientException) {
            // 연결 실패·타임아웃·역직렬화 오류 등
            throw OAuthProviderUnavailableException(e)
        }

        val oauthId = response?.sub ?: throw OAuthTokenInvalidException()
        return OAuthProfile(
            oauthId = oauthId,
            email = response.email,
            nickname = response.name,
            emailVerified = response.emailVerified ?: false,
        )
    }

    /** @deprecated Task 4에서 fetchKakaoProfile 자체가 인터페이스에서 제거된다. Google은 처음부터 미지원. */
    override fun fetchKakaoProfile(accessToken: String): OAuthProfile =
        throw UnsupportedOperationException("Google은 fetchKakaoProfile 미지원")

    data class GoogleUserInfoResponse(
        val sub: String? = null,
        val email: String? = null,
        @JsonProperty("email_verified") val emailVerified: Boolean? = null,
        val name: String? = null,
    )
}
