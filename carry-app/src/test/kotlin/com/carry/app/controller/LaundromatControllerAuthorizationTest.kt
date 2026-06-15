package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.laundromat.adapter.inbound.rest.LaundromatController
import com.carry.laundromat.application.port.inbound.LaundromatCommandUseCase
import com.carry.laundromat.application.port.inbound.LaundromatQueryUseCase
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.Location
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [LaundromatController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class LaundromatControllerAuthorizationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var laundromatCommandUseCase: LaundromatCommandUseCase

    @MockkBean
    lateinit var laundromatQueryUseCase: LaundromatQueryUseCase

    private val now = Instant.parse("2026-06-16T00:00:00Z")

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    private fun sampleLaundromat() = Laundromat.reconstitute(
        id = 1L,
        name = "테스트 세탁소",
        address = LaundromatAddress("서울 강남대로 1", null, null),
        location = Location(37.5, 127.0),
        options = emptySet(),
        mediaResources = emptyList(),
        createdAt = now,
        updatedAt = now,
    )

    private val registerBody = """{"name":"세탁소","roadAddress":"서울 강남대로 1","latitude":37.5,"longitude":127.0,"options":[]}"""
    private val updateInfoBody = """{"name":"세탁소","roadAddress":"서울 강남대로 1","latitude":37.5,"longitude":127.0}"""
    private val updateOptionsBody = """{"options":[]}"""
    private val addMediaBody = """{"url":"https://cdn.example.com/a.jpg","extension":"jpg"}"""

    // ── 운영 역할(COORDINATOR·ADMIN)은 관리할 수 있다 ──

    @Test
    fun `COORDINATOR는 세탁소를 등록할 수 있다`() {
        every { laundromatCommandUseCase.register(any(), any(), any(), any()) } returns sampleLaundromat()

        mockMvc.post("/api/v2/laundromats") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
            with(roleAuth("COORDINATOR"))
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        verify { laundromatCommandUseCase.register(any(), any(), any(), any()) }
    }

    @Test
    fun `ADMIN도 세탁소를 등록할 수 있다`() {
        every { laundromatCommandUseCase.register(any(), any(), any(), any()) } returns sampleLaundromat()

        mockMvc.post("/api/v2/laundromats") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
            with(roleAuth("ADMIN"))
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        verify { laundromatCommandUseCase.register(any(), any(), any(), any()) }
    }

    // ── 비운영 역할(CUSTOMER·CARRIER)은 403, 유스케이스 미호출 ──

    @Test
    fun `CUSTOMER가 세탁소를 등록하면 403이고 유스케이스는 호출되지 않는다`() {
        mockMvc.post("/api/v2/laundromats") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
            with(roleAuth("CUSTOMER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.register(any(), any(), any(), any()) }
    }

    @Test
    fun `CARRIER가 세탁소를 등록하면 403이다`() {
        mockMvc.post("/api/v2/laundromats") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
            with(roleAuth("CARRIER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.register(any(), any(), any(), any()) }
    }

    @Test
    fun `CUSTOMER가 세탁소 정보를 수정하면 403이고 유스케이스는 호출되지 않는다`() {
        mockMvc.put("/api/v2/laundromats/1") {
            contentType = MediaType.APPLICATION_JSON
            content = updateInfoBody
            with(roleAuth("CUSTOMER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.updateInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `CUSTOMER가 세탁소 옵션을 수정하면 403이고 유스케이스는 호출되지 않는다`() {
        mockMvc.put("/api/v2/laundromats/1/options") {
            contentType = MediaType.APPLICATION_JSON
            content = updateOptionsBody
            with(roleAuth("CUSTOMER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.updateOptions(any(), any()) }
    }

    @Test
    fun `CUSTOMER가 세탁소 이미지를 추가하면 403이고 유스케이스는 호출되지 않는다`() {
        mockMvc.post("/api/v2/laundromats/1/media") {
            contentType = MediaType.APPLICATION_JSON
            content = addMediaBody
            with(roleAuth("CUSTOMER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.addMediaResource(any(), any(), any()) }
    }

    @Test
    fun `CUSTOMER가 세탁소 이미지를 삭제하면 403이고 유스케이스는 호출되지 않는다`() {
        mockMvc.delete("/api/v2/laundromats/1/media/2") {
            with(roleAuth("CUSTOMER"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.removeMediaResource(any(), any()) }
    }

    // ── 조회는 역할 게이트가 없다(고객 탐색용) ──

    @Test
    fun `CUSTOMER도 세탁소 상세를 조회할 수 있다`() {
        every { laundromatQueryUseCase.getById(1L) } returns sampleLaundromat()

        mockMvc.get("/api/v2/laundromats/1") {
            with(roleAuth("CUSTOMER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(1) }
        }

        verify { laundromatQueryUseCase.getById(1L) }
    }

    // ── 인증 없는 관리 요청은 401 ──

    @Test
    fun `인증 없이 세탁소를 등록하면 401이다`() {
        mockMvc.post("/api/v2/laundromats") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(exactly = 0) { laundromatCommandUseCase.register(any(), any(), any(), any()) }
    }
}
