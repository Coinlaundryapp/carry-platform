package com.carry.user.adapter.inbound.rest.dto

import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.inbound.TokenPair
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Kakao 로그인 요청")
data class LoginRequest(
    @Schema(description = "Kakao access token") @field:NotBlank val kakaoAccessToken: String,
)

@Schema(description = "회원가입 완료 요청")
data class SignupRequest(
    @Schema(description = "로그인 단계에서 받은 가입 토큰") @field:NotBlank val signupToken: String,
    @Schema(description = "이름") @field:NotBlank val name: String,
    @Schema(description = "전화번호") @field:NotBlank val phone: String,
    @Schema(description = "이메일") @field:NotBlank val email: String,
)

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @Schema(description = "refresh token") @field:NotBlank val refreshToken: String,
)

@Schema(description = "가입 폼 prefill")
data class PrefillResponse(
    val email: String?,
    val nickname: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "로그인 응답 (status로 분기)")
data class LoginResponse(
    @Schema(description = "REGISTERED | REGISTRATION_REQUIRED") val status: String,
    val accessToken: String?,
    val refreshToken: String?,
    val signupToken: String?,
    val prefill: PrefillResponse?,
) {
    companion object {
        fun from(result: LoginResult): LoginResponse = when (result) {
            is LoginResult.Registered -> LoginResponse(
                status = "REGISTERED",
                accessToken = result.tokens.accessToken,
                refreshToken = result.tokens.refreshToken,
                signupToken = null,
                prefill = null,
            )
            is LoginResult.RegistrationRequired -> LoginResponse(
                status = "REGISTRATION_REQUIRED",
                accessToken = null,
                refreshToken = null,
                signupToken = result.signupToken,
                prefill = PrefillResponse(result.prefill.email, result.prefill.nickname),
            )
        }
    }
}

@Schema(description = "access/refresh 토큰 응답")
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(tokens: TokenPair) = TokenResponse(tokens.accessToken, tokens.refreshToken)
    }
}
