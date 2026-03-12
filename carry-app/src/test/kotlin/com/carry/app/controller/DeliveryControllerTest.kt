package com.carry.app.controller

import com.carry.delivery.adapter.inbound.rest.DeliveryController
import com.carry.delivery.application.port.inbound.DeliveryCommandUseCase
import com.carry.delivery.application.port.inbound.DeliveryQueryUseCase
import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.model.DeliveryStep
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
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

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [DeliveryController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
class DeliveryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var deliveryCommandUseCase: DeliveryCommandUseCase

    @MockkBean
    lateinit var deliveryQueryUseCase: DeliveryQueryUseCase

    private val now = Instant.now()

    private fun carrierAuth(userId: Long = 100L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))),
    )

    private fun sampleDelivery(
        id: Long = 1L,
        status: DeliveryStatus = DeliveryStatus.PICKUP_PENDING,
    ): Delivery {
        val steps = DeliveryStepType.entries.map { stepType ->
            DeliveryStep.reconstitute(
                id = stepType.ordinal + 1L,
                deliveryId = id,
                stepType = stepType,
                status = StepStatus.PENDING,
                mediaIds = emptyList(),
                note = null,
                completedAt = null,
            )
        }
        return Delivery.reconstitute(
            id = id, orderId = 1L, dispatchId = 10L, carrierId = 100L,
            laundromatId = 200L, status = status, actualWeight = null,
            steps = steps, createdAt = now, updatedAt = now,
        )
    }

    @Nested
    inner class GetMyDeliveries {

        @Test
        fun `내 배달 목록 조회 시 200을 반환한다`() {
            every { deliveryQueryUseCase.getDeliveriesByCarrier(any(), any(), any()) } returns
                listOf(sampleDelivery())

            mockMvc.get("/api/v2/deliveries/my") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].status") { value("PICKUP_PENDING") }
            }
        }
    }

    @Nested
    inner class GetDelivery {

        @Test
        fun `배달 상세 조회 시 200을 반환한다`() {
            every { deliveryQueryUseCase.getDelivery(1L) } returns sampleDelivery()

            mockMvc.get("/api/v2/deliveries/1") {
                with(carrierAuth())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(1) }
                jsonPath("$.data.steps.length()") { value(5) }
            }
        }
    }

    @Nested
    inner class CompletePickup {

        @Test
        fun `수거 완료 요청 시 200을 반환한다`() {
            every { deliveryCommandUseCase.completePickup(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                sampleDelivery(status = DeliveryStatus.PICKED_UP)

            mockMvc.post("/api/v2/deliveries/1/pickup") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "weight": 5.5,
                        "photoIds": [1, 2],
                        "customerId": 200,
                        "laundryItemType": "REGULAR",
                        "orderUnitType": "SOLO",
                        "orderRequestType": "NEW",
                        "selectedOptions": []
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("PICKED_UP") }
            }
        }
    }

    @Nested
    inner class StartWashing {

        @Test
        fun `세탁 시작 요청 시 200을 반환한다`() {
            every { deliveryCommandUseCase.startWashing(any(), any()) } returns
                sampleDelivery(status = DeliveryStatus.IN_LAUNDRY)

            mockMvc.post("/api/v2/deliveries/1/washing") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"photoIds": [3]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("IN_LAUNDRY") }
            }

            verify { deliveryCommandUseCase.startWashing(1L, listOf(3L)) }
        }
    }

    @Nested
    inner class CompleteDrying {

        @Test
        fun `건조 완료 요청 시 200을 반환한다`() {
            every { deliveryCommandUseCase.completeDrying(any(), any()) } returns
                sampleDelivery(status = DeliveryStatus.LAUNDRY_COMPLETE)

            mockMvc.post("/api/v2/deliveries/1/drying") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"photoIds": [4]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("LAUNDRY_COMPLETE") }
            }
        }
    }

    @Nested
    inner class CompleteDelivery {

        @Test
        fun `배달 완료 요청 시 200을 반환한다`() {
            every { deliveryCommandUseCase.completeDelivery(any(), any()) } returns
                sampleDelivery(status = DeliveryStatus.DELIVERED)

            mockMvc.post("/api/v2/deliveries/1/delivery") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"photoIds": [5]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("DELIVERED") }
            }
        }

        @Test
        fun `사진 없이 배달 완료 요청 시 400을 반환한다`() {
            mockMvc.post("/api/v2/deliveries/1/delivery") {
                with(carrierAuth())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"photoIds": []}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }
}
