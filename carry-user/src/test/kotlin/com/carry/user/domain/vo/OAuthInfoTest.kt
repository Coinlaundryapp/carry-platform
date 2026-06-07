package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OAuthInfoTest {

    @Test
    fun `유효한 OAuth 정보를 생성한다`() {
        val info = OAuthInfo(OAuthProvider.KAKAO, "kakao-123")
        assertThat(info.provider).isEqualTo(OAuthProvider.KAKAO)
        assertThat(info.id).isEqualTo("kakao-123")
    }

    @Test
    fun `빈 OAuth ID는 거부한다`() {
        assertThatThrownBy { OAuthInfo(OAuthProvider.KAKAO, "") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("OAuth ID")
    }

    @Test
    fun `동일한 값의 OAuthInfo는 동등하다`() {
        val a = OAuthInfo(OAuthProvider.KAKAO, "id-1")
        val b = OAuthInfo(OAuthProvider.KAKAO, "id-1")
        assertThat(a).isEqualTo(b)
    }
}
