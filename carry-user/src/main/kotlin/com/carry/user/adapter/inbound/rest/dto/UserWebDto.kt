package com.carry.user.adapter.inbound.rest.dto

import com.carry.user.domain.model.User
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "프로필 수정 요청")
data class UpdateProfileRequest(
    @Schema(description = "이름", example = "홍길동")
    @field:NotBlank val name: String,
    @Schema(description = "휴대전화번호", example = "010-1234-5678")
    @field:NotBlank val phone: String,
)

@Schema(description = "사용자 프로필 응답")
data class UserProfileResponse(
    @Schema(description = "사용자 ID", example = "1")
    val id: Long,
    @Schema(description = "이메일", example = "user@example.com")
    val email: String,
    @Schema(description = "이름", example = "홍길동")
    val name: String,
    @Schema(description = "휴대전화번호", example = "010-1234-5678")
    val phone: String,
    @Schema(description = "역할", example = "CUSTOMER", allowableValues = ["CUSTOMER", "CARRIER", "ADMIN"])
    val role: String,
    @Schema(description = "활성 상태")
    val isActive: Boolean,
) {
    companion object {
        fun from(user: User) = UserProfileResponse(
            id = user.id!!,
            email = user.email.value,
            name = user.name,
            phone = user.phone.value,
            role = user.role.name,
            isActive = user.isActive,
        )
    }
}
