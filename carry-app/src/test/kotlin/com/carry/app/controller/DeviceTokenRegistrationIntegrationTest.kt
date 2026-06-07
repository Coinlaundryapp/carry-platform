package com.carry.app.controller

import com.carry.app.test.IntegrationTestBase
import com.carry.notification.adapter.outbound.persistence.repository.DeviceTokenJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class DeviceTokenRegistrationIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var deviceTokenJpaRepository: DeviceTokenJpaRepository

    @BeforeEach
    fun setUp() {
        deviceTokenJpaRepository.deleteAll()
    }

    private fun auth(userId: Long) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))),
    )

    private fun register(userId: Long, token: String) =
        mockMvc.post("/api/v2/notifications/device-tokens") {
            with(auth(userId))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "$token", "platform": "web"}"""
        }

    @Test
    fun `동일 토큰을 두 번 등록해도 행은 하나이고 소유자와 lastSeenAt이 갱신된다`() {
        register(userId = 1L, token = "fcm-xyz").andExpect { status { isCreated() } }
        val first = deviceTokenJpaRepository.findByToken("fcm-xyz")!!

        register(userId = 2L, token = "fcm-xyz").andExpect { status { isCreated() } }

        assertThat(deviceTokenJpaRepository.count()).isEqualTo(1L)
        val second = deviceTokenJpaRepository.findByToken("fcm-xyz")!!
        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.userId).isEqualTo(2L)
        assertThat(second.lastSeenAt).isAfterOrEqualTo(first.lastSeenAt)
    }

    @Test
    fun `사용자별 활성 토큰을 조회할 수 있다`() {
        register(userId = 1L, token = "fcm-a").andExpect { status { isCreated() } }
        register(userId = 1L, token = "fcm-b").andExpect { status { isCreated() } }

        val tokens = deviceTokenJpaRepository.findByUserId(1L).map { it.token }
        assertThat(tokens).containsExactlyInAnyOrder("fcm-a", "fcm-b")
    }
}
