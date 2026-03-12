package com.carry.operation.adapter.inbound.rest.dto

import com.carry.operation.domain.model.OperationEvent
import com.carry.operation.domain.model.OperationSummary
import com.carry.operation.domain.model.Term
import com.carry.operation.domain.vo.TermType
import java.time.Instant

data class CreateTermRequest(
    val title: String,
    val content: String,
    val type: TermType,
    val required: Boolean,
)

data class UpdateTermRequest(
    val title: String,
    val content: String,
    val required: Boolean,
)

data class TermResponse(
    val id: Long,
    val title: String,
    val content: String,
    val type: String,
    val required: Boolean,
    val version: Int,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(term: Term) = TermResponse(
            id = term.id!!,
            title = term.title,
            content = term.content,
            type = term.type.name,
            required = term.required,
            version = term.version,
            active = term.active,
            createdAt = term.createdAt,
            updatedAt = term.updatedAt,
        )
    }
}

data class OperationSummaryResponse(
    val totalOrdersToday: Long,
    val pendingDispatches: Long,
    val activeDeliveries: Long,
    val completedToday: Long,
    val cancelledToday: Long,
) {
    companion object {
        fun from(summary: OperationSummary) = OperationSummaryResponse(
            totalOrdersToday = summary.totalOrdersToday,
            pendingDispatches = summary.pendingDispatches,
            activeDeliveries = summary.activeDeliveries,
            completedToday = summary.completedToday,
            cancelledToday = summary.cancelledToday,
        )
    }
}

data class OperationEventResponse(
    val id: Long,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: Long,
    val summary: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: OperationEvent) = OperationEventResponse(
            id = event.id!!,
            eventType = event.eventType,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            summary = event.summary,
            createdAt = event.createdAt,
        )
    }
}
