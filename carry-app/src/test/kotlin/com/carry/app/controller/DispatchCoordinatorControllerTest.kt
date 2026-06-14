package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.dispatch.adapter.inbound.rest.DispatchCoordinatorController
import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.temporal.ChronoUnit

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [DispatchCoordinatorController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class DispatchCoordinatorControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dispatchCommandUseCase: DispatchCommandUseCase

    @MockkBean
    lateinit var dispatchQueryUseCase: DispatchQueryUseCase

    @MockkBean
    lateinit var carrierAreaUseCase: CarrierAreaUseCase

    private val now = Instant.now()

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    private fun sampleDispatch() = Dispatch.reconstitute(
        id = 1L, orderId = 1L, laundromatId = 10L, status = DispatchStatus.PENDING,
        carrierId = null, areaCode = "GANGNAM", desiredPickupAt = now.plus(2, ChronoUnit.HOURS),
        assignedBy = null, assignedAt = null, acceptedAt = null, cancelReason = null,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `코디네이터가 상태·권역으로 배차 목록을 조회하면 200을 반환한다`() {
        every {
            dispatchQueryUseCase.getDispatchesForCoordinator(DispatchStatus.PENDING, "GANGNAM", null, 20)
        } returns listOf(sampleDispatch())

        mockMvc.get("/api/v2/coordinator/dispatches?status=PENDING&areaCode=GANGNAM") {
            with(roleAuth("COORDINATOR"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(1) }
            jsonPath("$.data[0].status") { value("PENDING") }
            jsonPath("$.data[0].areaCode") { value("GANGNAM") }
        }

        verify { dispatchQueryUseCase.getDispatchesForCoordinator(DispatchStatus.PENDING, "GANGNAM", null, 20) }
    }

    @Test
    fun `필터 없이 조회하면 전체를 위임한다`() {
        every { dispatchQueryUseCase.getDispatchesForCoordinator(null, null, null, 20) } returns emptyList()

        mockMvc.get("/api/v2/coordinator/dispatches") {
            with(roleAuth("COORDINATOR"))
        }.andExpect {
            status { isOk() }
        }

        verify { dispatchQueryUseCase.getDispatchesForCoordinator(null, null, null, 20) }
    }

    @Test
    fun `코디네이터 역할이 아니면 403을 반환한다`() {
        mockMvc.get("/api/v2/coordinator/dispatches") {
            with(roleAuth("CUSTOMER"))
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { dispatchQueryUseCase.getDispatchesForCoordinator(any(), any(), any(), any()) }
    }

    @Test
    fun `인증 없이 접근하면 401을 반환한다`() {
        mockMvc.get("/api/v2/coordinator/dispatches").andExpect {
            status { isUnauthorized() }
        }
    }
}
