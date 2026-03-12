package com.carry.order.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.order.adapter.inbound.rest.dto.CancelOrderRequest
import com.carry.order.adapter.inbound.rest.dto.CreateOrderRequest
import com.carry.order.adapter.inbound.rest.dto.OrderResponse
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.inbound.SelectedOptionCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Order", description = "주문 관리 API")
@RestController
@RequestMapping("/api/v2/orders")
class OrderController(
    private val orderCommandUseCase: OrderCommandUseCase,
    private val orderQueryUseCase: OrderQueryUseCase,
) {
    @Operation(summary = "주문 생성", description = "세탁물 수거/배달 주문을 생성합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "201", description = "주문 생성 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"), SwaggerApiResponse(responseCode = "422", description = "서비스 불가 지역 또는 시간")])
    @PostMapping
    fun createOrder(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val command = CreateOrderCommand(
            customerId = userId,
            shippingAddressId = request.shippingAddressId,
            laundromatId = request.laundromatId,
            laundryItemType = request.laundryItemType,
            selectedOptions = request.selectedOptions.map { SelectedOptionCommand(it.optionType, it.subOptionType) },
            desiredPickupAt = request.desiredPickupAt,
            desiredDeliveryAt = request.desiredDeliveryAt,
        )
        val order = orderCommandUseCase.createOrder(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(OrderResponse.from(order)))
    }

    @Operation(summary = "내 주문 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "주문 목록 조회 성공")])
    @GetMapping("/my")
    fun getMyOrders(@Parameter(hidden = true) @AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<List<OrderResponse>>> {
        val orders = orderQueryUseCase.getOrdersByCustomer(userId)
        return ResponseEntity.ok(ApiResponse.success(orders.map { OrderResponse.from(it) }))
    }

    @Operation(summary = "주문 상세 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "주문 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")])
    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderQueryUseCase.getOrder(orderId)
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)))
    }

    @Operation(summary = "주문 취소", description = "생성된 주문을 취소합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "주문 취소 성공"), SwaggerApiResponse(responseCode = "400", description = "취소할 수 없는 상태"), SwaggerApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")])
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @Valid @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<Void> {
        orderCommandUseCase.cancelOrder(orderId, request.reason, "CUSTOMER")
        return ResponseEntity.noContent().build()
    }
}
