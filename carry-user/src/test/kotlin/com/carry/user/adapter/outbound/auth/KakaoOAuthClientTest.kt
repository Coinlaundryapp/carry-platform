package com.carry.user.adapter.outbound.auth

import com.carry.user.domain.exception.OAuthProviderUnavailableException
import com.carry.user.domain.exception.OAuthTokenInvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient

class KakaoOAuthClientTest {

    private val builder = RestClient.builder().baseUrl("https://kapi.kakao.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val sut = KakaoOAuthClient(builder.build())

    @Test
    fun `user_me 응답을 OAuthProfile로 매핑한다`() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-at"))
            .andRespond(
                withSuccess(
                    """{"id":123456789,"kakao_account":{"email":"a@b.com","profile":{"nickname":"닉네임"}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val profile = sut.fetchKakaoProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("123456789")
        assertThat(profile.email).isEqualTo("a@b.com")
        assertThat(profile.nickname).isEqualTo("닉네임")
    }

    @Test
    fun `email_nickname이 없으면 null로 매핑한다`() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(withSuccess("""{"id":999}""", MediaType.APPLICATION_JSON))

        val profile = sut.fetchKakaoProfile("test-at")

        assertThat(profile.oauthId).isEqualTo("999")
        assertThat(profile.email).isNull()
        assertThat(profile.nickname).isNull()
    }

    @Test
    fun `id가 없으면 토큰 무효로 간주한다`() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(withSuccess("""{"kakao_account":{"email":"a@b.com"}}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { sut.fetchKakaoProfile("test-at") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `401이면 토큰 무효 예외를 던진다`() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { sut.fetchKakaoProfile("bad") }
            .isInstanceOf(OAuthTokenInvalidException::class.java)
    }

    @Test
    fun `5xx면 제공자 장애 예외를 던진다`() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(withServerError())

        assertThatThrownBy { sut.fetchKakaoProfile("test-at") }
            .isInstanceOf(OAuthProviderUnavailableException::class.java)
    }
}
