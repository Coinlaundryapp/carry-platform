package com.carry.operation.adapter.inbound.rest.dto

import com.carry.operation.domain.model.OperationEvent
import com.carry.operation.domain.model.OperationSummary
import com.carry.operation.domain.model.Term
import com.carry.operation.domain.vo.TermType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "약관 생성 요청")
data class CreateTermRequest(
    @Schema(description = "약관 제목")
    @field:NotBlank val title: String,
    @Schema(description = "약관 내용")
    @field:NotBlank val content: String,
    @Schema(description = "약관 유형")
    val type: TermType,
    @Schema(description = "필수 동의 여부")
    val required: Boolean,
)

@Schema(description = "약관 수정 요청")
data class UpdateTermRequest(
    @Schema(description = "약관 제목")
    @field:NotBlank val title: String,
    @Schema(description = "약관 내용")
    @field:NotBlank val content: String,
    @Schema(description = "필수 동의 여부")
    val required: Boolean,
)

@Schema(description = "이용약관 응답")
data class TermResponse(
    @Schema(description = "약관 ID") val id: Long,
    @Schema(description = "제목") val title: String,
    @Schema(description = "내용") val content: String,
    @Schema(description = "약관 유형") val type: String,
    @Schema(description = "필수 여부") val required: Boolean,
    @Schema(description = "버전") val version: Int,
    @Schema(description = "활성 상태") val active: Boolean,
    @Schema(description = "생성 시간") val createdAt: Instant,
    @Schema(description = "수정 시간") val updatedAt: Instant,
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

@Schema(description = "운영 요약 응답")
data class OperationSummaryResponse(
    @Schema(description = "오늘 총 주문 수") val totalOrdersToday: Long,
    @Schema(description = "대기 중 배차 수") val pendingDispatches: Long,
    @Schema(description = "진행 중 배달 수") val activeDeliveries: Long,
    @Schema(description = "오늘 완료 수") val completedToday: Long,
    @Schema(description = "오늘 취소 수") val cancelledToday: Long,
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

@Schema(description = "운영 이벤트 응답")
data class OperationEventResponse(
    @Schema(description = "이벤트 ID") val id: Long,
    @Schema(description = "이벤트 유형") val eventType: String,
    @Schema(description = "집합체 유형") val aggregateType: String,
    @Schema(description = "집합체 ID") val aggregateId: Long,
    @Schema(description = "요약") val summary: String,
    @Schema(description = "생성 시간") val createdAt: Instant,
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
