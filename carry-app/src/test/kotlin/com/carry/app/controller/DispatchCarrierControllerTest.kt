package com.carry.app.controller

import com.carry.dispatch.adapter.inbound.rest.DispatchCarrierController
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [DispatchCarrierController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
class DispatchCarrierControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dispatchCommandUseCase: DispatchCommandUseCase

    @MockkBean
    lateinit var dispatchQueryUseCase: DispatchQueryUseCase

    private val now = Instant.now()
    private val pickupAt = now.plus(2, ChronoUnit.HOURS)

    private fun carrierAuth(userId: Long = 100L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))),
    )

    private fun sampleDispatch(
        id: Long = 1L,
        status: DispatchStatus = DispatchStatus.PENDING,
        carrierId: Long? = null,
    ) = Dispatch.reconstitute(
        id = id, orderId = 1L, laundromatId = 10L, status = status,
        carrierId = carrierId, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
        assignedBy = if (carrierId != null) AssignedBy.CARRIER else null,
        assignedAt = if (carrierId != null) now else null,
        acceptedAt = null, cancelReason = null, createdAt = now, updatedAt = now,
    )

    @Nested
    inner class GetAvailableDispatches {

        @Test
        fun `수락 가능한 배차 목록 조회 시 200을 반환한다`() {
            every { dispatchQueryUseCase.getAvailableDispatches(any(), any(), any()) } returns
                listOf(sampleDispatch())

            mockMvc.get("/api/v2/dispatches/available") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].status") { value("PENDING") }
            }
        }
    }

    @Nested
    inner class ClaimDispatch {

        @Test
        fun `배차 선점 시 200을 반환한다`() {
            every { dispatchCommandUseCase.claimDispatch(any()) } returns
                sampleDispatch(status = DispatchStatus.ACCEPTED, carrierId = 100L)

            mockMvc.post("/api/v2/dispatches/1/claim") {
                with(carrierAuth())
                with(csrf())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("ACCEPTED") }
                jsonPath("$.data.carrierId") { value(100) }
            }
        }
    }

    @Nested
    inner class AcceptAssignment {

        @Test
        fun `배차 수락 시 200을 반환한다`() {
            every { dispatchCommandUseCase.acceptAssignment(any()) } returns
                sampleDispatch(status = DispatchStatus.ACCEPTED, carrierId = 100L)

            mockMvc.post("/api/v2/dispatches/1/accept") {
                with(carrierAuth())
                with(csrf())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("ACCEPTED") }
            }
        }
    }

    @Nested
    inner class RejectAssignment {

        @Test
        fun `배차 거절 시 200을 반환한다`() {
            every { dispatchCommandUseCase.rejectAssignment(any()) } returns
                sampleDispatch(status = DispatchStatus.PENDING)

            mockMvc.post("/api/v2/dispatches/1/reject") {
                with(carrierAuth())
                with(csrf())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("PENDING") }
            }
        }
    }

    @Nested
    inner class GetMyDispatches {

        @Test
        fun `내 배차 목록 조회 시 200을 반환한다`() {
            every { dispatchQueryUseCase.getDispatchesByCarrier(any(), any(), any()) } returns
                listOf(sampleDispatch(carrierId = 100L))

            mockMvc.get("/api/v2/dispatches/my") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
            }
        }
    }

    @Nested
    inner class GetDispatch {

        @Test
        fun `배차 상세 조회 시 200을 반환한다`() {
            every { dispatchQueryUseCase.getDispatch(1L) } returns sampleDispatch()

            mockMvc.get("/api/v2/dispatches/1") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(1) }
                jsonPath("$.data.areaCode") { value("GANGNAM") }
            }
        }
    }
}
