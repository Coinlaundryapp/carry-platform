package com.carry.user.adapter.inbound.rest

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DevLoginGuardTest {

    @Test
    fun `prod 프로파일이 없으면 통과한다`() {
        assertThatCode { DevLoginGuard.assertNotProd(listOf("local")) }.doesNotThrowAnyException()
        assertThatCode { DevLoginGuard.assertNotProd(listOf("dev")) }.doesNotThrowAnyException()
    }

    @Test
    fun `prod 프로파일이 함께 있으면 기동을 실패시킨다 — dev,prod 모순 봉인`() {
        assertThatThrownBy { DevLoginGuard.assertNotProd(listOf("dev", "prod")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("prod")
    }
}
