package com.carry.user.adapter.outbound.auth

import com.carry.common.exception.BusinessException
import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.domain.vo.OAuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OAuthProfileClientResolverTest {

    private class FakeClient(private val provider: OAuthProvider) : OAuthProfileClient {
        override fun supports(): OAuthProvider = provider
        override fun fetchProfile(accessToken: String): OAuthProfile =
            OAuthProfile(oauthId = "id", email = null, nickname = null)
        override fun fetchKakaoProfile(accessToken: String): OAuthProfile =
            throw UnsupportedOperationException()
    }

    private val kakao = FakeClient(OAuthProvider.KAKAO)
    private val naver = FakeClient(OAuthProvider.NAVER)
    private val google = FakeClient(OAuthProvider.GOOGLE)
    private val sut = OAuthProfileClientResolver(listOf(kakao, naver, google))

    @Test
    fun `provider별로 맞는 클라이언트를 반환한다`() {
        assertThat(sut.resolve(OAuthProvider.KAKAO)).isSameAs(kakao)
        assertThat(sut.resolve(OAuthProvider.NAVER)).isSameAs(naver)
        assertThat(sut.resolve(OAuthProvider.GOOGLE)).isSameAs(google)
    }

    @Test
    fun `지원하지 않는 provider면 BusinessException을 던진다`() {
        assertThatThrownBy { sut.resolve(OAuthProvider.DEV) }
            .isInstanceOf(BusinessException::class.java)
    }
}
