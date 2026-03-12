package com.carry.notification.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.notification.adapter.inbound.rest.dto.NotificationResponse
import com.carry.notification.application.port.inbound.NotificationQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v2/notifications")
class NotificationController(
    private val notificationQueryUseCase: NotificationQueryUseCase,
) {

    @Operation(summary = "내 알림 목록 조회", description = "커서 기반 페이지네이션으로 내 알림을 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "알림 목록 조회 성공")])
    @GetMapping("/my")
    fun getMyNotifications(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "커서 (마지막 알림 ID)") @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<NotificationResponse>>> {
        val notifications = notificationQueryUseCase.getNotificationsByRecipient(userId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(notifications.map { NotificationResponse.from(it) }))
    }

    @Operation(summary = "알림 상세 조회")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "알림 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "알림을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{notificationId}")
    fun getNotification(
        @PathVariable notificationId: Long,
    ): ResponseEntity<ApiResponse<NotificationResponse>> {
        val notification = notificationQueryUseCase.getNotification(notificationId)
        return ResponseEntity.ok(ApiResponse.success(NotificationResponse.from(notification)))
    }
}
