package com.carry.operation.adapter.inbound.rest

import com.carry.operation.adapter.inbound.rest.dto.OperationEventResponse
import com.carry.operation.adapter.inbound.rest.dto.OperationSummaryResponse
import com.carry.operation.application.port.inbound.OperationQueryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/admin/dashboard")
class OperationDashboardController(
    private val operationQueryUseCase: OperationQueryUseCase,
) {

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<OperationSummaryResponse> {
        val summary = operationQueryUseCase.getSummary()
        return ResponseEntity.ok(OperationSummaryResponse.from(summary))
    }

    @GetMapping("/events")
    fun getRecentEvents(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<List<OperationEventResponse>> {
        val events = operationQueryUseCase.getRecentEvents(limit)
        return ResponseEntity.ok(events.map { OperationEventResponse.from(it) })
    }
}
