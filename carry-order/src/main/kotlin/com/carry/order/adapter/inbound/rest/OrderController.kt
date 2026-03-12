package com.carry.order.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.order.adapter.inbound.rest.dto.CancelOrderRequest
import com.carry.order.adapter.inbound.rest.dto.CreateOrderRequest
import com.carry.order.adapter.inbound.rest.dto.OrderResponse
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.inbound.SelectedOptionCommand
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

@RestController
@RequestMapping("/api/v2/orders")
class OrderController(
    private val orderCommandUseCase: OrderCommandUseCase,
    private val orderQueryUseCase: OrderQueryUseCase,
) {
    @PostMapping
    fun createOrder(
        @AuthenticationPrincipal userId: Long,
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

    @GetMapping("/my")
    fun getMyOrders(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<List<OrderResponse>>> {
        val orders = orderQueryUseCase.getOrdersByCustomer(userId)
        return ResponseEntity.ok(ApiResponse.success(orders.map { OrderResponse.from(it) }))
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderQueryUseCase.getOrder(orderId)
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)))
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @Valid @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<Void> {
        orderCommandUseCase.cancelOrder(orderId, request.reason, "CUSTOMER")
        return ResponseEntity.noContent().build()
    }
}
