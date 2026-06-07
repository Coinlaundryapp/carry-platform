package com.carry.user.adapter.outbound.auth

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class KakaoBaseUrlGuardTest {

    private fun guard(profile: String, baseUrl: String): KakaoBaseUrlGuard {
        val env = MockEnvironment().apply { setActiveProfiles(profile) }
        return KakaoBaseUrlGuard(KakaoProperties(baseUrl = baseUrl), env)
    }

    @Test
    fun `prod에서 base-url이 실 Kakao가 아니면 부팅을 막는다`() {
        val sut = guard("prod", "http://kakao-stub:8089")

        assertThatThrownBy { sut.verify() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `prod에서 base-url이 실 Kakao면 통과한다`() {
        val sut = guard("prod", "https://kapi.kakao.com")

        assertThatCode { sut.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `비prod에서는 스텁 base-url을 허용한다`() {
        val sut = guard("stg", "http://kakao-stub:8089")

        assertThatCode { sut.verify() }.doesNotThrowAnyException()
    }
}
