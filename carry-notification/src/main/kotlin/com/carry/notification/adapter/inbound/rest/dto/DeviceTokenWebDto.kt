package com.carry.notification.adapter.inbound.rest.dto

import com.carry.notification.domain.model.DeviceToken
import com.carry.notification.domain.vo.DevicePlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

@Schema(description = "디바이스 토큰 등록 요청")
data class RegisterDeviceTokenRequest(
    @Schema(description = "FCM registration token", example = "fcm_registration_token_value")
    @field:NotBlank
    val token: String,

    @Schema(description = "플랫폼 (현재 web만 지원)", example = "web", defaultValue = "web")
    // Pattern은 대소문자 구분(정확히 "web")이라 검증을 먼저 통과한 값만 fromWire에 도달한다.
    @field:Pattern(regexp = "web", message = "지원하지 않는 platform입니다 (web만 허용)")
    val platform: String = "web",
) {
    fun toPlatform(): DevicePlatform = DevicePlatform.fromWire(platform)
}

@Schema(description = "디바이스 토큰 등록 응답")
data class DeviceTokenResponse(
    @Schema(description = "등록된 토큰") val token: String,
    @Schema(description = "플랫폼") val platform: String,
    @Schema(description = "최근 등록/갱신 시각") val lastSeenAt: Instant,
) {
    companion object {
        fun from(deviceToken: DeviceToken) = DeviceTokenResponse(
            token = deviceToken.token,
            platform = deviceToken.platform.name.lowercase(),
            lastSeenAt = deviceToken.lastSeenAt,
        )
    }
}
