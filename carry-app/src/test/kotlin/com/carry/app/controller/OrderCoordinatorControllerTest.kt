package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.order.adapter.inbound.rest.OrderCoordinatorController
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [OrderCoordinatorController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class OrderCoordinatorControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var orderCommandUseCase: OrderCommandUseCase

    @MockkBean
    lateinit var orderQueryUseCase: OrderQueryUseCase

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    private val now: Instant = Instant.now()

    private fun anOrder() = Order.reconstitute(
        id = 1L, customerId = 7L, status = OrderStatus.IN_PROGRESS,
        laundromatId = 10L, laundryItemType = "REGULAR",
        selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = OrderShippingAddress(
            "서울특별시 강남구 역삼로 1", "101호", "06230",
            37.5, 127.0, "홍길동", "01012345678", null, "GANGNAM",
        ),
        desiredPickupAt = now, desiredDeliveryAt = now.plus(4, ChronoUnit.HOURS),
        carrierId = null, actualWeight = null,
        cancellation = null, completedAt = null, createdAt = now, updatedAt = now,
    )

    @Test
    fun `코디네이터가 주문 취소를 요청하면 204를 반환하고 COORDINATOR로 취소한다`() {
        every { orderCommandUseCase.cancelOrder(1L, "운영 취소", "COORDINATOR") } returns Unit

        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("COORDINATOR"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isNoContent() }
        }

        verify { orderCommandUseCase.cancelOrder(1L, "운영 취소", "COORDINATOR") }
    }

    @Test
    fun `코디네이터 역할이 아니면 403을 반환하고 취소를 호출하지 않는다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("CUSTOMER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { orderCommandUseCase.cancelOrder(any(), any(), any()) }
    }

    @Test
    fun `인증 없이 접근하면 401을 반환한다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `취소 사유가 없으면 400을 반환한다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("COORDINATOR"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `코디네이터가 주문 목록을 상태 필터로 조회하면 200과 목록을 반환한다`() {
        every { orderQueryUseCase.getOrdersForCoordinator(OrderStatus.IN_PROGRESS, null, 20) } returns listOf(anOrder())

        mockMvc.get("/api/v2/coordinator/orders?status=IN_PROGRESS") {
            with(roleAuth("COORDINATOR"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(1) }
            jsonPath("$.data[0].status") { value("IN_PROGRESS") }
        }

        verify { orderQueryUseCase.getOrdersForCoordinator(OrderStatus.IN_PROGRESS, null, 20) }
    }

    @Test
    fun `코디네이터가 주문 단건을 조회하면 200을 반환한다`() {
        every { orderQueryUseCase.getOrderForCoordinator(1L) } returns anOrder()

        mockMvc.get("/api/v2/coordinator/orders/1") {
            with(roleAuth("COORDINATOR"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(1) }
        }
    }

    @Test
    fun `코디네이터 역할이 아니면 목록 조회도 403을 반환한다`() {
        mockMvc.get("/api/v2/coordinator/orders") {
            with(roleAuth("CUSTOMER"))
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { orderQueryUseCase.getOrdersForCoordinator(any(), any(), any()) }
    }
}
