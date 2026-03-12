package com.carry.app.controller

import com.carry.order.adapter.inbound.rest.OrderController
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
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
    controllers = [OrderController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
class OrderControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var orderCommandUseCase: OrderCommandUseCase

    @MockkBean
    lateinit var orderQueryUseCase: OrderQueryUseCase

    private val now = Instant.now()
    private val pickupAt = now.plus(2, ChronoUnit.HOURS)
    private val deliveryAt = now.plus(6, ChronoUnit.HOURS)

    private val address = OrderShippingAddress(
        roadAddress = "서울특별시 강남구 역삼로 1",
        detailAddress = "101호",
        zipCode = "06230",
        latitude = 37.5,
        longitude = 127.0,
        recipientName = "홍길동",
        recipientPhone = "01012345678",
        entranceInfo = null,
        areaCode = "GANGNAM",
    )

    private fun carrierAuth(userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))),
    )

    private fun sampleOrder(id: Long = 1L, status: OrderStatus = OrderStatus.CREATED) = Order.reconstitute(
        id = id, customerId = 1L, status = status, laundromatId = 10L,
        laundryItemType = "REGULAR",
        selectedOptions = listOf(SelectedOption("WASH", "STANDARD")),
        shippingAddress = address,
        desiredPickupAt = pickupAt, desiredDeliveryAt = deliveryAt,
        carrierId = null, invoiceId = null, totalAmount = null, actualWeight = null,
        cancelReason = null, cancelledBy = null, cancelledAt = null, completedAt = null,
        createdAt = now, updatedAt = now,
    )

    @Nested
    inner class CreateOrder {

        @Test
        fun `주문 생성 요청 시 201을 반환한다`() {
            every { orderCommandUseCase.createOrder(any()) } returns sampleOrder()

            mockMvc.post("/api/v2/orders") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "shippingAddressId": 1,
                        "laundromatId": 10,
                        "laundryItemType": "REGULAR",
                        "selectedOptions": [{"optionType": "WASH", "subOptionType": "STANDARD"}],
                        "desiredPickupAt": "$pickupAt",
                        "desiredDeliveryAt": "$deliveryAt"
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.id") { value(1) }
                jsonPath("$.data.status") { value("CREATED") }
            }

            verify(exactly = 1) { orderCommandUseCase.createOrder(any()) }
        }
    }

    @Nested
    inner class GetMyOrders {

        @Test
        fun `내 주문 목록 조회 시 200을 반환한다`() {
            every { orderQueryUseCase.getOrdersByCustomer(any(), any(), any()) } returns
                listOf(sampleOrder(1L), sampleOrder(2L))

            mockMvc.get("/api/v2/orders/my") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(2) }
            }
        }

        @Test
        fun `커서 파라미터를 전달하면 UseCase에 전달된다`() {
            every { orderQueryUseCase.getOrdersByCustomer(any(), eq(100L), eq(10)) } returns emptyList()

            mockMvc.get("/api/v2/orders/my") {
                with(carrierAuth())
                param("cursor", "100")
                param("size", "10")
            }.andExpect {
                status { isOk() }
            }

            verify { orderQueryUseCase.getOrdersByCustomer(any(), 100L, 10) }
        }
    }

    @Nested
    inner class GetOrder {

        @Test
        fun `주문 상세 조회 시 200을 반환한다`() {
            every { orderQueryUseCase.getOrder(1L) } returns sampleOrder()

            mockMvc.get("/api/v2/orders/1") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(1) }
                jsonPath("$.data.laundromatId") { value(10) }
            }
        }
    }

    @Nested
    inner class CancelOrder {

        @Test
        fun `주문 취소 요청 시 204를 반환한다`() {
            every { orderCommandUseCase.cancelOrder(1L, "고객 변심", "CUSTOMER") } returns Unit

            mockMvc.post("/api/v2/orders/1/cancel") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "고객 변심"}"""
            }.andExpect {
                status { isNoContent() }
            }

            verify { orderCommandUseCase.cancelOrder(1L, "고객 변심", "CUSTOMER") }
        }

        @Test
        fun `취소 사유가 없으면 400을 반환한다`() {
            mockMvc.post("/api/v2/orders/1/cancel") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class Security {

        @Test
        fun `인증 없이 접근하면 401을 반환한다`() {
            mockMvc.get("/api/v2/orders/my")
                .andExpect {
                    status { isUnauthorized() }
                }
        }
    }
}
