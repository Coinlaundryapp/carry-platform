package com.carry.notification.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.notification.adapter.inbound.rest.dto.DeviceTokenResponse
import com.carry.notification.adapter.inbound.rest.dto.RegisterDeviceTokenRequest
import com.carry.notification.application.port.inbound.RegisterDeviceTokenCommand
import com.carry.notification.application.port.inbound.RegisterDeviceTokenUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Device Token", description = "푸시 디바이스 토큰 API")
@RestController
@RequestMapping("/api/v2/notifications")
class DeviceTokenController(
    private val registerDeviceTokenUseCase: RegisterDeviceTokenUseCase,
) {

    @Operation(summary = "디바이스 토큰 등록", description = "FCM 웹 푸시 토큰을 등록/갱신합니다 (토큰 기준 멱등 upsert)")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "토큰 등록 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"),
            SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        ],
    )
    @PostMapping("/device-tokens")
    fun registerDeviceToken(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ): ResponseEntity<ApiResponse<DeviceTokenResponse>> {
        val deviceToken = registerDeviceTokenUseCase.register(
            RegisterDeviceTokenCommand(
                userId = userId,
                token = request.token,
                platform = request.toPlatform(),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(DeviceTokenResponse.from(deviceToken)))
    }
}
