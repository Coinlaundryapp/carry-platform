package com.carry.order.adapter.inbound.rest

import com.carry.order.adapter.inbound.rest.dto.CancelOrderRequest
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.order.domain.vo.CancelledBy
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Order - Coordinator", description = "주문 코디네이터 운영 API")
@RestController
@RequestMapping("/api/v2/coordinator/orders")
@PreAuthorize("hasRole('COORDINATOR')")
class OrderCoordinatorController(
    private val orderCommandUseCase: OrderCommandUseCase,
) {

    @Operation(
        summary = "주문 취소 (코디네이터)",
        description = "코디네이터가 주문을 취소한다. 결제 완료(PAID) 주문은 즉시 종료가 아니라 환불 보상 트랜잭션을 시작한다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "204", description = "주문 취소 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"),
            SwaggerApiResponse(responseCode = "409", description = "취소할 수 없는 상태"),
        ],
    )
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @Valid @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<Void> {
        orderCommandUseCase.cancelOrder(orderId, request.reason, CancelledBy.COORDINATOR.name)
        return ResponseEntity.noContent().build()
    }
}
