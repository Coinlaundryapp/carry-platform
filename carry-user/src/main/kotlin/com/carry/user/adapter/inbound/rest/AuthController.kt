package com.carry.user.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.AccessTokenResponse
import com.carry.user.adapter.inbound.rest.dto.LoginRequest
import com.carry.user.adapter.inbound.rest.dto.LoginResponse
import com.carry.user.adapter.inbound.rest.dto.RefreshRequest
import com.carry.user.adapter.inbound.rest.dto.SignupRequest
import com.carry.user.adapter.inbound.rest.dto.TokenResponse
import com.carry.user.application.port.inbound.AuthUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API (Kakao 로그인 / 2-step 가입 / 토큰 재발급)")
@RestController
@RequestMapping("/api/v2/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
) {

    @Operation(summary = "Kakao 로그인", description = "Kakao access token을 검증한다. 기존 유저면 토큰, 신규면 가입 토큰을 반환한다.")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val result = authUseCase.loginWithKakao(request.kakaoAccessToken)
        return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(result)))
    }

    @Operation(summary = "회원가입 완료", description = "가입 토큰과 폼(name/phone/email)으로 회원가입을 완료하고 토큰을 발급한다.")
    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val tokens = authUseCase.completeSignup(request.signupToken, request.name, request.phone, request.email)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(TokenResponse.from(tokens)))
    }

    @Operation(summary = "토큰 재발급", description = "refresh 토큰으로 새 access 토큰을 발급한다.")
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ResponseEntity<ApiResponse<AccessTokenResponse>> {
        val accessToken = authUseCase.refresh(request.refreshToken)
        return ResponseEntity.ok(ApiResponse.success(AccessTokenResponse(accessToken)))
    }
}
