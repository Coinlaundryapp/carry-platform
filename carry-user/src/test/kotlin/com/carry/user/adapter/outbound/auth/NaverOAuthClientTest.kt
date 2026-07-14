package com.carry.user.adapter.outbound.auth

import com.carry.user.domain.exception.OAuthProviderUnavailableException
import com.carry.user.domain.exception.OAuthTokenInvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class NaverOAuthClientTest {

    private val builder = RestClient.builder().baseUrl("https://openapi.naver.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val sut = NaverOAuthClient(builder.build())

    @Test
    fun `nid_me 응답을 OAuthProfile로 매핑한다`() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-at"))
            .andRespond(
                withSuccess(
                    """{"resultcode":"00","message":"success","response":{"id":"naver-123","email":"u@n.com","name":"홍길동"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val profile = sut.fetchProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("naver-123")
        assertThat(profile.email).isEqualTo("u@n.com")
        assertThat(profile.nickname).isEqualTo("홍길동")
        assertThat(profile.emailVerified).isFalse()
    }

    @Test
    fun `email_name이 없으면 null로 매핑한다`() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(
                withSuccess(
                    """{"resultcode":"00","message":"success","response":{"id":"naver-999"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val profile = sut.fetchProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("naver-999")
        assertThat(profile.email).isNull()
        assertThat(profile.nickname).isNull()
    }

    @Test
    fun `id가 없으면 토큰 무효로 간주한다`() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(
                withSuccess(
                    """{"resultcode":"00","message":"success","response":{"email":"u@n.com"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { sut.fetchProfile("test-at") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `401이면 토큰 무효 예외를 던진다`() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { sut.fetchProfile("bad") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `5xx면 제공자 장애 예외를 던진다`() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(withServerError())

        assertThatThrownBy { sut.fetchProfile("test-at") }
            .isInstanceOf(OAuthProviderUnavailableException::class.java)
    }

    @Test
    fun `supports는 NAVER를 반환한다`() {
        assertThat(sut.supports()).isEqualTo(com.carry.user.domain.vo.OAuthProvider.NAVER)
    }
}
