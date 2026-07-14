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

class GoogleOAuthClientTest {

    private val builder = RestClient.builder().baseUrl("https://openidconnect.googleapis.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val sut = GoogleOAuthClient(builder.build())

    @Test
    fun `userinfo 응답을 OAuthProfile로 매핑한다`() {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-at"))
            .andRespond(
                withSuccess(
                    """{"sub":"g-123","email":"u@g.com","email_verified":true,"name":"Gil"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val profile = sut.fetchProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("g-123")
        assertThat(profile.email).isEqualTo("u@g.com")
        assertThat(profile.nickname).isEqualTo("Gil")
        assertThat(profile.emailVerified).isTrue()
    }

    @Test
    fun `email_verified가 없으면 false로 매핑한다`() {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andRespond(
                withSuccess("""{"sub":"g-999"}""", MediaType.APPLICATION_JSON),
            )

        val profile = sut.fetchProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("g-999")
        assertThat(profile.email).isNull()
        assertThat(profile.nickname).isNull()
        assertThat(profile.emailVerified).isFalse()
    }

    @Test
    fun `sub가 없으면 토큰 무효로 간주한다`() {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andRespond(
                withSuccess("""{"email":"u@g.com"}""", MediaType.APPLICATION_JSON),
            )

        assertThatThrownBy { sut.fetchProfile("test-at") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `401이면 토큰 무효 예외를 던진다`() {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { sut.fetchProfile("bad") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `5xx면 제공자 장애 예외를 던진다`() {
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
            .andRespond(withServerError())

        assertThatThrownBy { sut.fetchProfile("test-at") }
            .isInstanceOf(OAuthProviderUnavailableException::class.java)
    }

    @Test
    fun `supports는 GOOGLE을 반환한다`() {
        assertThat(sut.supports()).isEqualTo(com.carry.user.domain.vo.OAuthProvider.GOOGLE)
    }

    @Test
    fun `fetchKakaoProfile은 미지원 예외를 던진다`() {
        assertThatThrownBy { sut.fetchKakaoProfile("test-at") }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }
}
