package com.carry.operation.application.port.inbound

import com.carry.operation.domain.model.OperationEvent
import com.carry.operation.domain.model.OperationSummary

interface OperationQueryUseCase {
    fun getSummary(): OperationSummary
    fun getRecentEvents(limit: Int = 50): List<OperationEvent>
}
