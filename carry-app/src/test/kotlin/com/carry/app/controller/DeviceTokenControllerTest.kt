package com.carry.app.controller

import com.carry.notification.adapter.inbound.rest.DeviceTokenController
import com.carry.notification.application.port.inbound.RegisterDeviceTokenUseCase
import com.carry.notification.domain.model.DeviceToken
import com.carry.notification.domain.vo.DevicePlatform
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [DeviceTokenController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
class DeviceTokenControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var registerDeviceTokenUseCase: RegisterDeviceTokenUseCase

    private fun userAuth(userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))),
    )

    private fun sampleToken(token: String = "fcm-abc") = DeviceToken.reconstitute(
        id = 1L, userId = 1L, token = token, platform = DevicePlatform.WEB,
        createdAt = Instant.parse("2026-05-28T00:00:00Z"),
        lastSeenAt = Instant.parse("2026-05-28T00:00:00Z"),
    )

    @Nested
    inner class Register {

        @Test
        fun `토큰 등록 요청 시 201을 반환한다`() {
            every { registerDeviceTokenUseCase.register(any()) } returns sampleToken()

            mockMvc.post("/api/v2/notifications/device-tokens") {
                with(userAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "fcm-abc", "platform": "web"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.token") { value("fcm-abc") }
                jsonPath("$.data.platform") { value("web") }
                jsonPath("$.data.lastSeenAt") { exists() }
            }

            verify(exactly = 1) { registerDeviceTokenUseCase.register(any()) }
        }

        @Test
        fun `platform 누락 시 web 기본값으로 201을 반환한다`() {
            every { registerDeviceTokenUseCase.register(any()) } returns sampleToken()

            mockMvc.post("/api/v2/notifications/device-tokens") {
                with(userAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "fcm-abc"}"""
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        fun `token이 비어있으면 400을 반환한다`() {
            mockMvc.post("/api/v2/notifications/device-tokens") {
                with(userAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "", "platform": "web"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `지원하지 않는 platform이면 400을 반환한다`() {
            mockMvc.post("/api/v2/notifications/device-tokens") {
                with(userAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "fcm-abc", "platform": "ios"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class Security {

        @Test
        fun `인증 없이 접근하면 401을 반환한다`() {
            mockMvc.post("/api/v2/notifications/device-tokens") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "fcm-abc", "platform": "web"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
        }
    }
}
