package com.carry.app.admin

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.response.ApiResponse
import com.carry.infra.kafka.dlq.DlqRedriveService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * DLQ 운영 엔드포인트 — 런북(kafka-dlq-nonempty)의 수동 재처리 절차 자동화.
 *
 * 크로스 인프라(carry-infra-kafka) 운영 작업이라 도메인 모듈이 아닌 carry-app에 둔다
 * ([com.carry.app.metrics.OutboxMetricsScheduler]와 같은 위치 결정).
 */
@Tag(name = "Admin DLQ", description = "DLQ 재처리 운영 API")
@RestController
@RequestMapping("/api/v2/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
class AdminDlqController(
    private val dlqRedriveService: DlqRedriveService,
    private val auditPort: AuditPort,
) {

    @Operation(
        summary = "DLQ 재처리(redrive)",
        description = "지정 토픽의 DLQ에서 최대 maxRecords건을 원본 토픽으로 재발행합니다. " +
            "재발행 한도(3회) 도달 메시지는 보류(parked)되어 DLQ에 남습니다. 민감 운영 작업으로 감사 로그에 기록됩니다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "재처리 완료"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 토픽명 또는 maxRecords 범위 초과"),
            SwaggerApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
        ],
    )
    @PostMapping("/redrive")
    fun redrive(@RequestBody request: DlqRedriveRequest): ResponseEntity<ApiResponse<DlqRedriveResponse>> {
        val result = dlqRedriveService.redrive(request.topic, request.maxRecords)
        auditPort.record(
            action = AuditAction.DLQ_REDRIVE,
            targetType = "KafkaTopic",
            targetId = request.topic,
            before = null,
            after = result,
        )
        return ResponseEntity.ok(ApiResponse.success(DlqRedriveResponse(result.redriven, result.parked)))
    }
}

@Schema(description = "DLQ 재처리 요청")
data class DlqRedriveRequest(
    @field:Schema(description = "원본 토픽명(.DLQ 접미사 제외)", example = "order.event")
    val topic: String,
    @field:Schema(description = "이번 호출에서 처리할 최대 건수", example = "100", defaultValue = "100")
    val maxRecords: Int = 100,
)

@Schema(description = "DLQ 재처리 결과")
data class DlqRedriveResponse(
    @field:Schema(description = "원본 토픽으로 재발행된 건수")
    val redriven: Int,
    @field:Schema(description = "재발행 한도 도달로 보류된 건수(DLQ 잔류, 수동 검토 대상)")
    val parked: Int,
)
