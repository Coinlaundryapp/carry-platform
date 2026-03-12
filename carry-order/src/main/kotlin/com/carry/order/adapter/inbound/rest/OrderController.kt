package com.carry.order.adapter.inbound.rest

import com.carry.order.adapter.inbound.rest.dto.CancelOrderRequest
import com.carry.order.adapter.inbound.rest.dto.CreateOrderRequest
import com.carry.order.adapter.inbound.rest.dto.OrderResponse
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.inbound.SelectedOptionCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/orders")
class OrderController(
    private val orderCommandUseCase: OrderCommandUseCase,
    private val orderQueryUseCase: OrderQueryUseCase,
) {
    @PostMapping
    fun createOrder(
        @RequestParam customerId: Long, // TODO: JWT에서 추출
        @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val command = CreateOrderCommand(
            customerId = customerId,
            shippingAddressId = request.shippingAddressId,
            laundromatId = request.laundromatId,
            laundryItemType = request.laundryItemType,
            selectedOptions = request.selectedOptions.map { SelectedOptionCommand(it.optionType, it.subOptionType) },
            desiredPickupAt = request.desiredPickupAt,
            desiredDeliveryAt = request.desiredDeliveryAt,
        )
        val order = orderCommandUseCase.createOrder(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order))
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<OrderResponse> {
        val order = orderQueryUseCase.getOrder(orderId)
        return ResponseEntity.ok(OrderResponse.from(order))
    }

    @GetMapping("/my")
    fun getMyOrders(@RequestParam customerId: Long): ResponseEntity<List<OrderResponse>> {
        val orders = orderQueryUseCase.getOrdersByCustomer(customerId)
        return ResponseEntity.ok(orders.map { OrderResponse.from(it) })
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<Void> {
        orderCommandUseCase.cancelOrder(orderId, request.reason, "CUSTOMER")
        return ResponseEntity.noContent().build()
    }
}
